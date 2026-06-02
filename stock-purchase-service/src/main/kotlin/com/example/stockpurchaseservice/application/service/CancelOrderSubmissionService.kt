package com.example.stockpurchaseservice.application.service

import com.example.common.UseCaseImpl
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionCommand
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionResult
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionUseCase
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.BrokerOrderRejectedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionUnknownException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderTemporaryUnavailableException
import com.example.stockpurchaseservice.application.port.out.CancelOrderDto
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.OrderCancellationSubmissionAlert
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.ProcessedEventPort
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.util.UUID

@UseCaseImpl
class CancelOrderSubmissionService(
    private val marketService: MarketServicePort,
    private val processedEventPort: ProcessedEventPort,
    private val operationalAlertPort: OperationalAlertPort,
    private val orderIntentSubmissionPort: OrderIntentSubmissionPort,
) : CancelOrderSubmissionUseCase {

    override suspend fun execute(command: CancelOrderSubmissionCommand): CancelOrderSubmissionResult {
        val started = processedEventPort.tryStart(command.eventId, command.idempotencyKey)
        if (!started) {
            return CancelOrderSubmissionResult(CancelOrderSubmissionStatus.SKIPPED_DUPLICATE)
        }

        var originalSubmission: OrderIntentSubmissionDto? = null
        return try {
            originalSubmission = orderIntentSubmissionPort.findByExternalOrderId(command.originalBrokerOrderId)
            val submission = marketService.cancelOrder(command.toCancelOrderDto(originalSubmission))
            markOriginalOrderCancelPending(originalSubmission, command, "cancel request accepted by broker")
            processedEventPort.markSuccess(command.eventId)
            CancelOrderSubmissionResult(
                status = CancelOrderSubmissionStatus.ACCEPTED,
                brokerOrderId = submission.externalOrderId,
            )
        } catch (exception: BrokerOrderSubmissionUnknownException) {
            markOriginalOrderCancelPending(originalSubmission, command, exception.message)
            runCatching {
                operationalAlertPort.alertOrderCancellationSubmissionUnknown(command.toAlert(exception.message, originalSubmission))
            }
            processedEventPort.markSuccess(command.eventId)
            CancelOrderSubmissionResult(
                status = CancelOrderSubmissionStatus.SUBMISSION_UNKNOWN,
                brokerOrderId = exception.externalOrderId,
            )
        } catch (exception: BrokerOrderRejectedException) {
            runCatching {
                operationalAlertPort.alertOrderCancellationSubmissionFailed(command.toAlert(exception.message, originalSubmission))
            }
            processedEventPort.markSuccess(command.eventId)
            CancelOrderSubmissionResult(CancelOrderSubmissionStatus.REJECTED)
        } catch (exception: BrokerOrderTemporaryUnavailableException) {
            runCatching {
                operationalAlertPort.alertOrderCancellationSubmissionFailed(command.toAlert(exception.message, originalSubmission))
            }
            processedEventPort.markFailed(command.eventId, exception.message)
            throw exception
        } catch (exception: Throwable) {
            runCatching {
                operationalAlertPort.alertOrderCancellationSubmissionFailed(command.toAlert(exception.message, originalSubmission))
            }
            processedEventPort.markFailed(command.eventId, exception.message)
            throw exception
        }
    }

    private fun CancelOrderSubmissionCommand.toCancelOrderDto(
        originalSubmission: OrderIntentSubmissionDto?,
    ): CancelOrderDto {
        val market = originalSubmission?.market ?: symbol.toStockOrderMarket()
        val resolvedBranchOrderNumber = branchOrderNumber ?: originalSubmission?.branchOrderNumber
        if (market == StockOrderMarket.DOMESTIC) {
            require(!resolvedBranchOrderNumber.isNullOrBlank()) {
                "domestic cancellation requires branchOrderNumber"
            }
        }
        return CancelOrderDto(
            orderId = toInternalOrderId(),
            stockId = symbol,
            originalOrderId = originalBrokerOrderId,
            branchOrderNumber = resolvedBranchOrderNumber,
            quantity = quantity.toInt(),
            market = market,
            orderType = orderType.toStockOrderType(),
            price = price,
            cancelAll = cancelAll,
        )
    }

    private fun CancelOrderSubmissionCommand.toAlert(
        reason: String?,
        originalSubmission: OrderIntentSubmissionDto?,
    ): OrderCancellationSubmissionAlert {
        return OrderCancellationSubmissionAlert(
            cancellationRequestId = eventId,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = strategyExecutionId,
            symbol = symbol,
            originalBrokerOrderId = originalBrokerOrderId,
            branchOrderNumber = branchOrderNumber ?: originalSubmission?.branchOrderNumber,
            reason = reason,
            occurredAt = ZonedDateTime.now(),
        )
    }

    private suspend fun markOriginalOrderCancelPending(
        original: OrderIntentSubmissionDto?,
        command: CancelOrderSubmissionCommand,
        reason: String?,
    ) {
        original ?: return
        orderIntentSubmissionPort.saveCancelPending(
            original.copy(
                statusReason = reason,
                lastStatusCheckedAt = command.requestedAt,
            ),
        )
    }

    private fun CancelOrderSubmissionCommand.toInternalOrderId(): UUID {
        return UUID.nameUUIDFromBytes(idempotencyKey.toByteArray(StandardCharsets.UTF_8))
    }

    private fun String.toStockOrderMarket(): StockOrderMarket {
        return when {
            length == 6 && all(Char::isDigit) -> StockOrderMarket.DOMESTIC
            else -> StockOrderMarket.OVERSEAS_US
        }
    }

    private fun OrderIntentType.toStockOrderType(): StockOrderType {
        return when (this) {
            OrderIntentType.LOC -> StockOrderType.LOC
            OrderIntentType.MOC -> StockOrderType.MOC
            OrderIntentType.LIMIT -> StockOrderType.LIMIT
        }
    }
}

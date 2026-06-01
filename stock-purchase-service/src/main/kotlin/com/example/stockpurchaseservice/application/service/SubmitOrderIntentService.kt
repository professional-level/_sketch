package com.example.stockpurchaseservice.application.service

import com.example.common.UseCaseImpl
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentResult
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentUseCase
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionUnknownException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderTemporaryUnavailableException
import com.example.stockpurchaseservice.application.port.out.OrderExecutionEventPort
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentCommand
import com.example.stockpurchaseservice.application.port.out.OrderRiskControlPort
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionFailureAlert
import com.example.stockpurchaseservice.application.port.out.SubmissionUnknownAlert
import com.example.stockpurchaseservice.application.port.out.ProcessedEventPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.util.UUID

@UseCaseImpl
class SubmitOrderIntentService(
    private val marketService: MarketServicePort,
    private val processedEventPort: ProcessedEventPort,
    private val orderIntentSubmissionPort: OrderIntentSubmissionPort,
    private val orderExecutionEventPort: OrderExecutionEventPort,
    private val orderRiskControlPort: OrderRiskControlPort,
    private val operationalAlertPort: OperationalAlertPort,
) : SubmitOrderIntentUseCase {

    override suspend fun execute(command: SubmitOrderIntentCommand): SubmitOrderIntentResult {
        val started = processedEventPort.tryStart(command.eventId, command.idempotencyKey)
        if (!started) {
            return SubmitOrderIntentResult(OrderIntentSubmissionStatus.SKIPPED_DUPLICATE)
        }

        return try {
            val orderId = command.toInternalOrderId()
            val market = command.symbol.toStockOrderMarket()
            val orderType = command.orderType.toStockOrderType()
            val riskAssessment = orderRiskControlPort.assess(command.toRiskAssessmentCommand(orderId, market))
            if (!riskAssessment.accepted) {
                return rejectByRisk(command, orderId, riskAssessment.reason ?: "order rejected by risk policy")
            }

            val submission = submit(command, orderId, market, orderType)
            orderIntentSubmissionPort.saveSubmitted(submission)
            orderExecutionEventPort.publishSubmitted(command.toSubmittedMessage(submission))
            processedEventPort.markSuccess(command.eventId)
            SubmitOrderIntentResult(OrderIntentSubmissionStatus.SUBMITTED)
        } catch (exception: BrokerOrderSubmissionUnknownException) {
            val unknownSubmission = command.toUnknownSubmission(exception, command.toInternalOrderId())
            orderIntentSubmissionPort.saveUnknown(unknownSubmission)
            runCatching {
                operationalAlertPort.alertSubmissionUnknown(command.toSubmissionUnknownAlert(unknownSubmission, exception.message))
            }
            processedEventPort.markSuccess(command.eventId)
            SubmitOrderIntentResult(OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN)
        } catch (exception: BrokerOrderTemporaryUnavailableException) {
            runCatching {
                operationalAlertPort.alertOrderSubmissionFailed(command.toFailureAlert(exception))
            }
            processedEventPort.markFailed(command.eventId, exception.message)
            throw exception
        } catch (exception: Throwable) {
            runCatching {
                orderExecutionEventPort.publishRejected(command.toRejectedMessage(exception))
            }
            runCatching {
                operationalAlertPort.alertOrderSubmissionFailed(command.toFailureAlert(exception))
            }
            processedEventPort.markFailed(command.eventId, exception.message)
            throw exception
        }
    }

    private fun submit(
        command: SubmitOrderIntentCommand,
        orderId: UUID,
        market: StockOrderMarket,
        orderType: StockOrderType,
    ): OrderIntentSubmissionDto {
        val quantity = command.quantity.toInt()
        val submission = when (command.side) {
            OrderIntentSide.BUY -> marketService.buyStock(
                PurchaseOrderDto(
                    orderId = orderId,
                    stockId = command.symbol,
                    purchasePrice = checkNotNull(command.price),
                    quantity = quantity,
                    market = market,
                    orderType = orderType,
                ),
            )

            OrderIntentSide.SELL -> marketService.sellStock(
                SellingOrderDto(
                    orderId = orderId,
                    stockId = command.symbol,
                    sellingPrice = command.price ?: 0.0,
                    quantity = quantity,
                    market = market,
                    orderType = orderType,
                ),
            )
        }

        return OrderIntentSubmissionDto(
            orderIntentId = command.eventId,
            idempotencyKey = command.idempotencyKey,
            strategyExecutionId = command.strategyExecutionId,
            symbol = command.symbol,
            side = command.side,
            orderType = command.orderType,
            submittedPrice = command.price,
            quantity = command.quantity,
            orderTag = command.orderTag,
            internalOrderId = orderId,
            externalOrderId = submission.externalOrderId,
            submittedAt = ZonedDateTime.now(),
            status = OrderIntentSubmissionStatusDto.SUBMITTED,
        )
    }

    private suspend fun rejectByRisk(
        command: SubmitOrderIntentCommand,
        orderId: UUID,
        reason: String,
    ): SubmitOrderIntentResult {
        orderIntentSubmissionPort.saveRejected(command.toRejectedSubmission(orderId, reason))
        orderExecutionEventPort.publishRejected(command.toRejectedMessage(reason))
        processedEventPort.markSuccess(command.eventId)
        return SubmitOrderIntentResult(OrderIntentSubmissionStatus.REJECTED)
    }

    private fun SubmitOrderIntentCommand.toUnknownSubmission(
        exception: BrokerOrderSubmissionUnknownException,
        orderId: UUID,
    ): OrderIntentSubmissionDto {
        return OrderIntentSubmissionDto(
            orderIntentId = eventId,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = strategyExecutionId,
            symbol = symbol,
            side = side,
            orderType = orderType,
            submittedPrice = price,
            quantity = quantity,
            orderTag = orderTag,
            internalOrderId = orderId,
            externalOrderId = exception.externalOrderId,
            submittedAt = ZonedDateTime.now(),
            status = OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN,
            statusReason = exception.message,
            lastStatusCheckedAt = null,
        )
    }

    private fun SubmitOrderIntentCommand.toRejectedSubmission(
        orderId: UUID,
        reason: String,
    ): OrderIntentSubmissionDto {
        return OrderIntentSubmissionDto(
            orderIntentId = eventId,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = strategyExecutionId,
            symbol = symbol,
            side = side,
            orderType = orderType,
            submittedPrice = price,
            quantity = quantity,
            orderTag = orderTag,
            internalOrderId = orderId,
            externalOrderId = null,
            submittedAt = ZonedDateTime.now(),
            status = OrderIntentSubmissionStatusDto.REJECTED,
            statusReason = reason,
            lastStatusCheckedAt = null,
        )
    }

    private fun SubmitOrderIntentCommand.toSubmittedMessage(
        submission: OrderIntentSubmissionDto,
    ): OrderSubmittedMessage {
        return OrderSubmittedMessage(
            eventId = deterministicEventId("${eventId}:SUBMITTED"),
            strategyExecutionId = strategyExecutionId,
            orderIntentId = eventId.toString(),
            brokerOrderId = checkNotNull(submission.externalOrderId),
            submittedAt = submission.submittedAt,
        )
    }

    private fun SubmitOrderIntentCommand.toRejectedMessage(exception: Throwable): OrderRejectedMessage {
        return toRejectedMessage(exception.message ?: exception::class.java.simpleName)
    }

    private fun SubmitOrderIntentCommand.toRejectedMessage(reason: String): OrderRejectedMessage {
        val rejectedAt = ZonedDateTime.now()
        return OrderRejectedMessage(
            eventId = deterministicEventId("${eventId}:REJECTED"),
            strategyExecutionId = strategyExecutionId,
            orderIntentId = eventId.toString(),
            brokerOrderId = null,
            reason = reason,
            rejectedAt = rejectedAt,
        )
    }

    private fun SubmitOrderIntentCommand.toFailureAlert(exception: Throwable): OrderSubmissionFailureAlert {
        return OrderSubmissionFailureAlert(
            orderIntentId = eventId,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = strategyExecutionId,
            symbol = symbol,
            side = side,
            orderType = orderType,
            orderTag = orderTag,
            reason = exception.message ?: exception::class.java.simpleName,
            occurredAt = ZonedDateTime.now(),
        )
    }

    private fun SubmitOrderIntentCommand.toSubmissionUnknownAlert(
        submission: OrderIntentSubmissionDto,
        reason: String?,
    ): SubmissionUnknownAlert {
        val checkedAt = ZonedDateTime.now()
        return SubmissionUnknownAlert(
            orderIntentId = eventId,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = strategyExecutionId,
            symbol = symbol,
            side = side,
            orderType = orderType,
            orderTag = orderTag,
            externalOrderId = submission.externalOrderId,
            reason = reason,
            submittedAt = submission.submittedAt,
            checkedAt = checkedAt,
        )
    }

    private fun SubmitOrderIntentCommand.toRiskAssessmentCommand(
        orderId: UUID,
        market: StockOrderMarket,
    ): OrderRiskAssessmentCommand {
        return OrderRiskAssessmentCommand(
            orderIntentId = eventId,
            internalOrderId = orderId,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = strategyExecutionId,
            symbol = symbol,
            side = side,
            orderType = orderType,
            quantity = quantity,
            limitPrice = price,
            estimatedNotional = price?.let { it * quantity },
            market = market,
            orderTag = orderTag,
            createdAt = createdAt,
        )
    }

    private fun SubmitOrderIntentCommand.toInternalOrderId(): UUID {
        return UUID.nameUUIDFromBytes(idempotencyKey.toByteArray(StandardCharsets.UTF_8))
    }

    private fun deterministicEventId(seed: String): UUID {
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8))
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

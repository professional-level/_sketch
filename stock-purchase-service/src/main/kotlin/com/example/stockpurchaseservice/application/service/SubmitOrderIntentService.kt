package com.example.stockpurchaseservice.application.service

import com.example.common.UseCaseImpl
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentResult
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentUseCase
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OrderExecutionEventPort
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
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
) : SubmitOrderIntentUseCase {

    override suspend fun execute(command: SubmitOrderIntentCommand): SubmitOrderIntentResult {
        val started = processedEventPort.tryStart(command.eventId, command.idempotencyKey)
        if (!started) {
            return SubmitOrderIntentResult(OrderIntentSubmissionStatus.SKIPPED_DUPLICATE)
        }

        return try {
            val submission = submit(command)
            orderIntentSubmissionPort.saveSubmitted(submission)
            orderExecutionEventPort.publishSubmitted(command.toSubmittedMessage(submission))
            processedEventPort.markSuccess(command.eventId)
            SubmitOrderIntentResult(OrderIntentSubmissionStatus.SUBMITTED)
        } catch (exception: Throwable) {
            runCatching {
                orderExecutionEventPort.publishRejected(command.toRejectedMessage(exception))
            }
            processedEventPort.markFailed(command.eventId, exception.message)
            throw exception
        }
    }

    private fun submit(command: SubmitOrderIntentCommand): OrderIntentSubmissionDto {
        val orderId = UUID.nameUUIDFromBytes(command.idempotencyKey.toByteArray(StandardCharsets.UTF_8))
        val quantity = command.quantity.toInt()
        val market = command.symbol.toStockOrderMarket()
        val orderType = command.orderType.toStockOrderType()
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
        )
    }

    private fun SubmitOrderIntentCommand.toSubmittedMessage(
        submission: OrderIntentSubmissionDto,
    ): OrderSubmittedMessage {
        return OrderSubmittedMessage(
            eventId = deterministicEventId("${eventId}:SUBMITTED"),
            strategyExecutionId = strategyExecutionId,
            orderIntentId = eventId.toString(),
            brokerOrderId = submission.externalOrderId,
            submittedAt = submission.submittedAt,
        )
    }

    private fun SubmitOrderIntentCommand.toRejectedMessage(exception: Throwable): OrderRejectedMessage {
        val rejectedAt = ZonedDateTime.now()
        return OrderRejectedMessage(
            eventId = deterministicEventId("${eventId}:REJECTED"),
            strategyExecutionId = strategyExecutionId,
            orderIntentId = eventId.toString(),
            brokerOrderId = null,
            reason = exception.message ?: exception::class.java.simpleName,
            rejectedAt = rejectedAt,
        )
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

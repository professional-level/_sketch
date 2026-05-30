package com.example.stockpurchaseservice.application.service

import com.example.common.UseCaseImpl
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentResult
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentUseCase
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.ProcessedEventPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import java.nio.charset.StandardCharsets
import java.util.UUID

@UseCaseImpl
class SubmitOrderIntentService(
    private val marketService: MarketServicePort,
    private val processedEventPort: ProcessedEventPort,
) : SubmitOrderIntentUseCase {

    override suspend fun execute(command: SubmitOrderIntentCommand): SubmitOrderIntentResult {
        val started = processedEventPort.tryStart(command.eventId, command.idempotencyKey)
        if (!started) {
            return SubmitOrderIntentResult(OrderIntentSubmissionStatus.SKIPPED_DUPLICATE)
        }

        return runCatching {
            submit(command)
        }.onSuccess {
            processedEventPort.markSuccess(command.eventId)
        }.onFailure { exception ->
            processedEventPort.markFailed(command.eventId, exception.message)
        }.map {
            SubmitOrderIntentResult(OrderIntentSubmissionStatus.SUBMITTED)
        }.getOrThrow()
    }

    private fun submit(command: SubmitOrderIntentCommand) {
        val orderId = UUID.nameUUIDFromBytes(command.idempotencyKey.toByteArray(StandardCharsets.UTF_8))
        val quantity = command.quantity.toInt()
        when (command.side) {
            OrderIntentSide.BUY -> marketService.buyStock(
                PurchaseOrderDto(
                    orderId = orderId,
                    stockId = command.symbol,
                    purchasePrice = checkNotNull(command.price),
                    quantity = quantity,
                ),
            )

            OrderIntentSide.SELL -> marketService.sellStock(
                SellingOrderDto(
                    orderId = orderId,
                    stockId = command.symbol,
                    sellingPrice = command.price ?: 0.0,
                    quantity = quantity,
                ),
            )
        }
    }
}

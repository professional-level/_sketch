package com.example.stockpurchaseservice.application.port.`in`

import com.example.common.UseCase
import java.time.ZonedDateTime
import java.util.UUID

@UseCase
interface SubmitOrderIntentUseCase {
    suspend fun execute(command: SubmitOrderIntentCommand): SubmitOrderIntentResult
}

data class SubmitOrderIntentCommand(
    val eventId: UUID,
    val idempotencyKey: String,
    val strategyExecutionId: String,
    val symbol: String,
    val side: OrderIntentSide,
    val orderType: OrderIntentType,
    val price: Double?,
    val quantity: Long,
    val orderTag: String,
    val createdAt: ZonedDateTime,
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(strategyExecutionId.isNotBlank()) { "strategyExecutionId must not be blank" }
        require(symbol.isNotBlank()) { "symbol must not be blank" }
        require(quantity > 0) { "quantity must be positive" }
        require(quantity <= Int.MAX_VALUE) { "quantity must fit Int" }
        require(orderTag.isNotBlank()) { "orderTag must not be blank" }
        if (side == OrderIntentSide.BUY || orderType != OrderIntentType.MOC) {
            require(price != null && price > 0.0) { "$side $orderType price must be positive" }
        }
    }
}

data class SubmitOrderIntentResult(
    val status: OrderIntentSubmissionStatus,
)

enum class OrderIntentSubmissionStatus {
    SUBMITTED,
    SKIPPED_DUPLICATE,
}

enum class OrderIntentSide {
    BUY,
    SELL,
}

enum class OrderIntentType {
    LOC,
    MOC,
    LIMIT,
}

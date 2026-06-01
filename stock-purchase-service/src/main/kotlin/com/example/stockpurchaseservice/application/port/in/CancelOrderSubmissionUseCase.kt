package com.example.stockpurchaseservice.application.port.`in`

import com.example.common.UseCase
import java.time.ZonedDateTime
import java.util.UUID

@UseCase
interface CancelOrderSubmissionUseCase {
    suspend fun execute(command: CancelOrderSubmissionCommand): CancelOrderSubmissionResult
}

data class CancelOrderSubmissionCommand(
    val eventId: UUID,
    val idempotencyKey: String,
    val strategyExecutionId: String,
    val symbol: String,
    val originalBrokerOrderId: String,
    val branchOrderNumber: String? = null,
    val quantity: Long,
    val orderType: OrderIntentType = OrderIntentType.LIMIT,
    val price: Double = 0.0,
    val cancelAll: Boolean = true,
    val requestedAt: ZonedDateTime,
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(strategyExecutionId.isNotBlank()) { "strategyExecutionId must not be blank" }
        require(symbol.isNotBlank()) { "symbol must not be blank" }
        require(originalBrokerOrderId.isNotBlank()) { "originalBrokerOrderId must not be blank" }
        require(branchOrderNumber == null || branchOrderNumber.isNotBlank()) {
            "branchOrderNumber must not be blank"
        }
        require(quantity > 0) { "quantity must be positive" }
        require(quantity <= Int.MAX_VALUE) { "quantity must fit Int" }
        require(price >= 0.0) { "price must not be negative" }
    }
}

data class CancelOrderSubmissionResult(
    val status: CancelOrderSubmissionStatus,
    val brokerOrderId: String? = null,
)

enum class CancelOrderSubmissionStatus {
    ACCEPTED,
    SUBMISSION_UNKNOWN,
    SKIPPED_DUPLICATE,
    REJECTED,
}

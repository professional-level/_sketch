package com.example.strategyexecutionservice.application.port.`in`

import com.example.common.UseCase
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import java.time.ZonedDateTime

@UseCase
interface SignalLaorOrderMilestoneUseCase {
    suspend fun execute(command: SignalLaorOrderMilestoneCommand): SignalLaorOrderMilestoneResult
}

data class SignalLaorOrderMilestoneCommand(
    val eventId: String,
    val milestoneType: String,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String?,
    val orderTag: String?,
    val side: OrderSide?,
    val filledQuantity: Long,
    val averageFilledPrice: Double?,
    val occurredAt: ZonedDateTime,
    val sourceEventIds: List<String>,
    val idempotencyKey: String,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(milestoneType.isNotBlank()) { "milestoneType must not be blank" }
        require(strategyExecutionId.isNotBlank()) { "strategyExecutionId must not be blank" }
        require(orderIntentId.isNotBlank()) { "orderIntentId must not be blank" }
        brokerOrderId?.let { require(it.isNotBlank()) { "brokerOrderId must not be blank" } }
        orderTag?.let { require(it.isNotBlank()) { "orderTag must not be blank" } }
        require(filledQuantity >= 0) { "filledQuantity must not be negative" }
        averageFilledPrice?.let { require(it > 0.0) { "averageFilledPrice must be positive" } }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
    }
}

data class SignalLaorOrderMilestoneResult(
    val strategyExecutionId: String,
    val status: SignalLaorOrderMilestoneStatus,
    val skippedReason: String? = null,
)

enum class SignalLaorOrderMilestoneStatus {
    SIGNALED,
    SKIPPED_DUPLICATE,
    SIGNAL_SKIPPED,
}

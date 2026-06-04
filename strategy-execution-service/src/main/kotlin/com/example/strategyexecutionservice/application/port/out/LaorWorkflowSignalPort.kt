package com.example.strategyexecutionservice.application.port.out

import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import java.time.ZonedDateTime

interface LaorWorkflowSignalPort {
    suspend fun signal(signal: LaorOrderMilestoneSignal): LaorWorkflowSignalResult
}

data class LaorOrderMilestoneSignal(
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
    val workflowId: String = strategyExecutionId
}

data class LaorWorkflowSignalResult(
    val status: LaorWorkflowSignalStatus,
    val skippedReason: String? = null,
)

enum class LaorWorkflowSignalStatus {
    SIGNALED,
    SKIPPED,
}

package com.example.strategyexecutionservice.application.port.out

import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import java.time.ZonedDateTime

interface StrategyExecutionMilestoneEventPort {
    suspend fun tryRecord(event: StrategyExecutionMilestoneEventRecord): Boolean
}

data class StrategyExecutionMilestoneEventRecord(
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

package com.example.strategyexecutionservice.application.port.out

import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import java.time.ZonedDateTime

interface StrategyExecutionAnomalyEventPort {
    suspend fun tryRecord(event: StrategyExecutionAnomalyEventRecord): Boolean
}

data class StrategyExecutionAnomalyEventRecord(
    val eventId: String,
    val anomalyType: String,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String?,
    val orderTag: String?,
    val side: OrderSide?,
    val reason: String,
    val occurredAt: ZonedDateTime,
    val sourceEventIds: List<String>,
    val idempotencyKey: String,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(anomalyType.isNotBlank()) { "anomalyType must not be blank" }
        require(strategyExecutionId.isNotBlank()) { "strategyExecutionId must not be blank" }
        require(orderIntentId.isNotBlank()) { "orderIntentId must not be blank" }
        brokerOrderId?.let { require(it.isNotBlank()) { "brokerOrderId must not be blank" } }
        orderTag?.let { require(it.isNotBlank()) { "orderTag must not be blank" } }
        require(reason.isNotBlank()) { "reason must not be blank" }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
    }
}

package com.example.strategyexecutionservice.application.port.out

import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import java.time.ZonedDateTime

interface StrategyExecutionOrderEventPort {
    suspend fun tryRecord(event: StrategyExecutionOrderEventRecord): Boolean
}

data class StrategyExecutionOrderEventRecord(
    val eventId: String,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String?,
    val type: StrategyExecutionOrderEventType,
    val side: OrderSide? = null,
    val price: Double? = null,
    val quantity: Long? = null,
    val orderTag: String? = null,
    val reason: String? = null,
    val occurredAt: ZonedDateTime,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(strategyExecutionId.isNotBlank()) { "strategyExecutionId must not be blank" }
        require(orderIntentId.isNotBlank()) { "orderIntentId must not be blank" }
        brokerOrderId?.let { require(it.isNotBlank()) { "brokerOrderId must not be blank" } }
        price?.let { require(it > 0.0) { "price must be positive" } }
        quantity?.let { require(it > 0) { "quantity must be positive" } }
        orderTag?.let { require(it.isNotBlank()) { "orderTag must not be blank" } }
        reason?.let { require(it.isNotBlank()) { "reason must not be blank" } }
    }
}

enum class StrategyExecutionOrderEventType {
    SUBMITTED,
    REJECTED,
    CANCELLED,
    FILLED,
    PARTIALLY_FILLED,
}

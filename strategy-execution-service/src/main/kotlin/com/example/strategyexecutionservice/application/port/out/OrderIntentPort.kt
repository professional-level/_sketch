package com.example.strategyexecutionservice.application.port.out

import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.execution.OrderType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType
import java.time.ZonedDateTime
import java.util.UUID

interface OrderIntentPort {
    suspend fun publishAll(orderIntents: List<OrderIntentMessage>)
}

data class OrderIntentMessage(
    val eventId: UUID,
    val strategyExecutionId: String,
    val strategyType: StrategyExecutionType,
    val symbol: String,
    val side: OrderSide,
    val orderType: OrderType,
    val price: Double?,
    val quantity: Long,
    val orderTag: String,
    val idempotencyKey: String,
    val createdAt: ZonedDateTime,
)

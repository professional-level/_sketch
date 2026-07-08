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
    val tradingEnvironment: OrderTradingEnvironment = OrderTradingEnvironment.MOCK,
    val exchange: String = DEFAULT_OVERSEAS_ORDER_EXCHANGE,
    val executionRunId: String = "",
    val orderIndex: Int = 0,
    val market: String = "",
    val strategyVersion: String = "v1",
)

enum class OrderTradingEnvironment {
    MOCK,
    LIVE,
}

const val DEFAULT_OVERSEAS_ORDER_EXCHANGE = "NASD"

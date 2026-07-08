package com.example.stockpurchaseservice.adapter.out.broker

import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import java.time.Duration

internal interface BrokerGatewayRateLimiter {
    fun reserve(command: BrokerGatewayRateLimitCommand): BrokerGatewayRateLimitResult
}

internal data class BrokerGatewayRateLimitCommand(
    val operation: BrokerGatewayOperation,
    val market: StockOrderMarket,
    val isMock: Boolean,
)

internal data class BrokerGatewayRateLimitResult(
    val allowed: Boolean,
    val remainingTokens: Long,
    val retryAfter: Duration,
)

internal enum class BrokerGatewayOperation {
    SUBMIT_ORDER,
    CANCEL_ORDER,
    FIND_CANCELABLE_ORDERS,
    FIND_ORDER_HISTORY,
    FIND_ACCOUNT_SNAPSHOT,
}

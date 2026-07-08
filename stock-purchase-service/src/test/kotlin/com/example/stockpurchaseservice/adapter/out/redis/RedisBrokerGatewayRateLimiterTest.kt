package com.example.stockpurchaseservice.adapter.out.redis

import com.example.stockpurchaseservice.adapter.out.broker.BrokerGatewayOperation
import com.example.stockpurchaseservice.adapter.out.broker.BrokerGatewayRateLimitCommand
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.config.redis.StockPurchaseRedisProperties
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisBrokerGatewayRateLimiterTest {

    @Test
    fun `reserves token with operation market and environment bucket`() {
        val runner = FakeRedisStringScriptRunner("1:4:0")
        val limiter = RedisBrokerGatewayRateLimiter(runner, properties())

        val result = limiter.reserve(
            BrokerGatewayRateLimitCommand(
                operation = BrokerGatewayOperation.SUBMIT_ORDER,
                market = StockOrderMarket.OVERSEAS_US,
                isMock = true,
            ),
        )

        assertTrue(result.allowed)
        assertEquals(4, result.remainingTokens)
        assertEquals(Duration.ZERO, result.retryAfter)
        with(runner.calls.single()) {
            assertEquals(
                listOf("akra:test:broker-rate-limit:submit_order:overseas_us:mock"),
                keys,
            )
            assertEquals("5", args[1])
            assertEquals("2", args[2])
            assertEquals("10000", args[3])
        }
    }

    @Test
    fun `denies reservation with retry after`() {
        val limiter = RedisBrokerGatewayRateLimiter(
            FakeRedisStringScriptRunner("0:0:500"),
            properties(),
        )

        val result = limiter.reserve(
            BrokerGatewayRateLimitCommand(
                operation = BrokerGatewayOperation.FIND_ORDER_HISTORY,
                market = StockOrderMarket.DOMESTIC,
                isMock = false,
            ),
        )

        assertFalse(result.allowed)
        assertEquals(0, result.remainingTokens)
        assertEquals(Duration.ofMillis(500), result.retryAfter)
    }

    @Test
    fun `fails closed when redis is unavailable by default`() {
        val limiter = RedisBrokerGatewayRateLimiter(
            FakeRedisStringScriptRunner(failure = IllegalStateException("redis down")),
            properties(),
        )

        val result = limiter.reserve(command())

        assertFalse(result.allowed)
        assertEquals(Duration.ofSeconds(10), result.retryAfter)
    }

    @Test
    fun `fails open when configured`() {
        val props = properties().apply {
            brokerRateLimit.failOpen = true
        }
        val limiter = RedisBrokerGatewayRateLimiter(
            FakeRedisStringScriptRunner(failure = IllegalStateException("redis down")),
            props,
        )

        val result = limiter.reserve(command())

        assertTrue(result.allowed)
        assertEquals(Duration.ZERO, result.retryAfter)
    }

    private fun command(): BrokerGatewayRateLimitCommand {
        return BrokerGatewayRateLimitCommand(
            operation = BrokerGatewayOperation.CANCEL_ORDER,
            market = StockOrderMarket.OVERSEAS_US,
            isMock = true,
        )
    }

    private fun properties(): StockPurchaseRedisProperties {
        return StockPurchaseRedisProperties().apply {
            keyPrefix = "akra:test:"
            brokerRateLimit.capacity = 5
            brokerRateLimit.refillPerSecond = 2
            brokerRateLimit.bucketTtl = Duration.ofSeconds(10)
            brokerRateLimit.failOpen = false
        }
    }
}

package com.example.stockpurchaseservice.adapter.out.broker

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderQueryFailedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderTemporaryUnavailableException
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RateLimitedBrokerGatewayTest {

    @Test
    fun `blocks order submission before broker delegate`() {
        val delegate = FakeBrokerGateway()
        val limiter = FakeBrokerGatewayRateLimiter(
            results = ArrayDeque(
                listOf(BrokerGatewayRateLimitResult(false, remainingTokens = 0, retryAfter = Duration.ofMillis(250))),
            ),
        )
        val gateway = RateLimitedBrokerGateway(delegate, limiter)

        assertFailsWith<BrokerOrderTemporaryUnavailableException> {
            gateway.submitOrder(orderCommand())
        }

        assertEquals(0, delegate.submitOrderCalls)
        assertEquals(
            listOf(BrokerGatewayRateLimitCommand(BrokerGatewayOperation.SUBMIT_ORDER, StockOrderMarket.OVERSEAS_US, true)),
            limiter.commands,
        )
    }

    @Test
    fun `delegates order submission when token is reserved`() {
        val delegate = FakeBrokerGateway()
        val limiter = FakeBrokerGatewayRateLimiter()
        val gateway = RateLimitedBrokerGateway(delegate, limiter)

        val result = gateway.submitOrder(orderCommand(isMock = false))

        assertEquals(BrokerOrderSubmissionDto("broker-1"), result)
        assertEquals(1, delegate.submitOrderCalls)
        assertEquals(
            listOf(BrokerGatewayRateLimitCommand(BrokerGatewayOperation.SUBMIT_ORDER, StockOrderMarket.OVERSEAS_US, false)),
            limiter.commands,
        )
    }

    @Test
    fun `blocks broker query with query failure`() {
        val delegate = FakeBrokerGateway()
        val limiter = FakeBrokerGatewayRateLimiter(
            results = ArrayDeque(
                listOf(BrokerGatewayRateLimitResult(false, remainingTokens = 0, retryAfter = Duration.ofSeconds(1))),
            ),
        )
        val gateway = RateLimitedBrokerGateway(delegate, limiter)

        assertFailsWith<BrokerOrderQueryFailedException> {
            gateway.findOrderHistory(BrokerOrderHistoryQuery(StockOrderMarket.DOMESTIC, isMock = true))
        }

        assertEquals(0, delegate.findOrderHistoryCalls)
        assertEquals(
            listOf(BrokerGatewayRateLimitCommand(BrokerGatewayOperation.FIND_ORDER_HISTORY, StockOrderMarket.DOMESTIC, true)),
            limiter.commands,
        )
    }

    private fun orderCommand(isMock: Boolean = true): BrokerOrderCommand {
        return BrokerOrderCommand(
            internalOrderId = UUID.randomUUID(),
            market = StockOrderMarket.OVERSEAS_US,
            side = OrderIntentSide.BUY,
            symbol = "TQQQ",
            orderType = StockOrderType.LOC,
            price = 100.0,
            quantity = 1,
            isMock = isMock,
        )
    }

    private class FakeBrokerGateway : BrokerGateway {
        var submitOrderCalls: Int = 0
        var findOrderHistoryCalls: Int = 0

        override fun submitOrder(command: BrokerOrderCommand): BrokerOrderSubmissionDto {
            submitOrderCalls += 1
            return BrokerOrderSubmissionDto("broker-1")
        }

        override fun cancelOrder(command: BrokerOrderCancelCommand): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto("broker-2")
        }

        override fun findCancelableOrders(query: BrokerOrderCancelableQuery): List<BrokerCancelableOrderItem> {
            return emptyList()
        }

        override fun findOrderHistory(query: BrokerOrderHistoryQuery): List<BrokerOrderHistoryItem> {
            findOrderHistoryCalls += 1
            return emptyList()
        }

        override fun findAccountSnapshot(query: BrokerAccountSnapshotQuery): BrokerAccountSnapshot {
            return BrokerAccountSnapshot(
                market = query.market,
                exchange = query.exchange,
                currency = query.currency,
                positions = emptyList(),
            )
        }
    }

    private class FakeBrokerGatewayRateLimiter(
        private val results: ArrayDeque<BrokerGatewayRateLimitResult> = ArrayDeque(),
    ) : BrokerGatewayRateLimiter {
        val commands: MutableList<BrokerGatewayRateLimitCommand> = mutableListOf()

        override fun reserve(command: BrokerGatewayRateLimitCommand): BrokerGatewayRateLimitResult {
            commands += command
            return results.removeFirstOrNull()
                ?: BrokerGatewayRateLimitResult(true, remainingTokens = 1, retryAfter = Duration.ZERO)
        }
    }
}

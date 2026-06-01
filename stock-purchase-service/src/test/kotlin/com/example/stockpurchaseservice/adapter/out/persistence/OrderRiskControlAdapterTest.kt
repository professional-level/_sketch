package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionSide
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderRiskSubmissionReader
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentCommand
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderRiskControlAdapterTest {

    @Test
    fun `rejects overseas LOC order after configured cutoff`() = runBlocking {
        val result = adapter().assess(
            command(
                orderType = OrderIntentType.LOC,
                createdAt = ZonedDateTime.parse("2026-06-01T15:51:00-04:00[America/New_York]"),
            ),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "outside OVERSEAS_US LOC order window")
    }

    @Test
    fun `accepts overseas LOC order before configured cutoff`() = runBlocking {
        val result = adapter().assess(
            command(
                orderType = OrderIntentType.LOC,
                createdAt = ZonedDateTime.parse("2026-06-01T15:49:00-04:00[America/New_York]"),
            ),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `rejects order on configured overseas market holiday`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.overseasUs.holidays = listOf("2026-06-01")
        }

        val result = adapter(properties).assess(
            command(createdAt = ZonedDateTime.parse("2026-06-01T10:00:00-04:00[America/New_York]")),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "market holiday")
    }

    @Test
    fun `applies configured early close to limit orders`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.overseasUs.earlyCloseDates = listOf("2026-06-01")
            tradingHours.overseasUs.earlyCloseTime = "13:00"
        }

        val result = adapter(properties).assess(
            command(
                orderType = OrderIntentType.LIMIT,
                createdAt = ZonedDateTime.parse("2026-06-01T13:01:00-04:00[America/New_York]"),
            ),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "allowed=09:30-13:00")
    }

    @Test
    fun `rejects order when early close configuration is invalid`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.overseasUs.earlyCloseDates = listOf("2026-06-01")
            tradingHours.overseasUs.earlyCloseTime = "invalid"
        }

        val result = adapter(properties).assess(
            command(createdAt = ZonedDateTime.parse("2026-06-01T10:00:00-04:00[America/New_York]")),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "invalid trading-hours close")
    }

    @Test
    fun `rejects buy when active pending buy notional plus new order exceeds account limit`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            maxAccountPendingBuyNotional = 1_000.0
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 850.0)

        val result = adapter(properties, reader).assess(
            command(quantity = 2, limitPrice = 100.0),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "account pending buy notional 1050.0 exceeds limit 1000.0")
    }

    @Test
    fun `accepts buy when projected pending buy notional equals account limit`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            maxAccountPendingBuyNotional = 1_000.0
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 800.0)

        val result = adapter(properties, reader).assess(
            command(quantity = 2, limitPrice = 100.0),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `does not apply pending buy exposure limit to sell orders`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            maxAccountPendingBuyNotional = 1_000.0
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 1_500.0)

        val result = adapter(properties, reader).assess(
            command(side = OrderIntentSide.SELL, orderType = OrderIntentType.LOC, quantity = 2, limitPrice = 100.0),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `skips trading hours guard when disabled`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.enabled = false
        }

        val result = adapter(properties).assess(
            command(createdAt = ZonedDateTime.parse("2026-06-06T15:51:00-04:00[America/New_York]")),
        )

        assertTrue(result.accepted)
    }

    private fun adapter(
        properties: OrderRiskProperties = OrderRiskProperties(),
        reader: OrderRiskSubmissionReader = FakeOrderRiskSubmissionReader(),
    ): OrderRiskControlAdapter {
        properties.duplicateOrderKillSwitchEnabled = false
        return OrderRiskControlAdapter(
            orderRiskSubmissionReader = reader,
            properties = properties,
        )
    }

    private fun command(
        side: OrderIntentSide = OrderIntentSide.BUY,
        orderType: OrderIntentType = OrderIntentType.LOC,
        market: StockOrderMarket = StockOrderMarket.OVERSEAS_US,
        quantity: Long = 1,
        limitPrice: Double? = 100.0,
        createdAt: ZonedDateTime = ZonedDateTime.parse("2026-06-01T10:00:00-04:00[America/New_York]"),
    ): OrderRiskAssessmentCommand {
        return OrderRiskAssessmentCommand(
            orderIntentId = UUID.randomUUID(),
            internalOrderId = UUID.randomUUID(),
            idempotencyKey = UUID.randomUUID().toString(),
            strategyExecutionId = "laor-v4:TQQQ",
            symbol = "TQQQ",
            side = side,
            orderType = orderType,
            quantity = quantity,
            limitPrice = limitPrice,
            estimatedNotional = limitPrice?.let { it * quantity },
            market = market,
            orderTag = "FIRST_BUY",
            createdAt = createdAt,
        )
    }

    private class FakeOrderRiskSubmissionReader(
        private val activeBuyNotional: Double = 0.0,
    ) : OrderRiskSubmissionReader {
        override suspend fun countBrokerSubmittedBetween(
            from: ZonedDateTime,
            to: ZonedDateTime,
        ): Long = 0

        override suspend fun existsActiveDuplicate(
            strategyExecutionId: String,
            symbol: String,
            side: OrderIntentSubmissionSide,
            orderTag: String,
            from: ZonedDateTime,
            to: ZonedDateTime,
        ): Boolean = false

        override suspend fun sumActiveBuyNotional(): Double = activeBuyNotional
    }
}

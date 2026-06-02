package com.example.stockpurchaseservice.application.scheduler

import com.example.stockpurchaseservice.application.port.`in`.CreateSellOrdersByStrategyUseCase
import com.example.stockpurchaseservice.application.port.`in`.RecoverUnknownOrderSubmissionsUseCase
import com.example.stockpurchaseservice.application.port.`in`.ReconcileExecutionsUseCase
import com.example.stockpurchaseservice.application.port.`in`.SimulateStockPurchaseUseCase
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StockTradeSchedulerTest {

    @Test
    fun `trading gate blocks default overseas us market holiday`() {
        val gate = TradingHoursStockTradeScheduleGate(OrderRiskProperties())

        val shouldRun = gate.shouldRunAt(ZonedDateTime.parse("2026-07-03T10:00:00-04:00[America/New_York]"))

        assertFalse(shouldRun)
    }

    @Test
    fun `trading gate allows regular overseas us trading day`() {
        val gate = TradingHoursStockTradeScheduleGate(OrderRiskProperties())

        val shouldRun = gate.shouldRunAt(ZonedDateTime.parse("2026-07-06T10:00:00-04:00[America/New_York]"))

        assertTrue(shouldRun)
    }

    @Test
    fun `trading gate falls back to existing scheduler behavior when disabled`() {
        val properties = OrderRiskProperties().apply {
            tradingHours.enabled = false
        }
        val gate = TradingHoursStockTradeScheduleGate(properties)

        val shouldRun = gate.shouldRunAt(ZonedDateTime.parse("2026-07-03T10:00:00-04:00[America/New_York]"))

        assertTrue(shouldRun)
    }

    @Test
    fun `scheduler skips jobs when trading gate is closed`() = runBlocking {
        val calls = mutableListOf<String>()
        val scheduler = scheduler(calls, StockTradeScheduleGate { false })

        scheduler.sellOrderByStrategies()
        scheduler.executionCheck()
        scheduler.simulateStockPurchase()

        assertEquals(emptyList(), calls)
    }

    @Test
    fun `scheduler runs jobs when trading gate is open`() = runBlocking {
        val calls = mutableListOf<String>()
        val scheduler = scheduler(calls, StockTradeScheduleGate { true })

        scheduler.sellOrderByStrategies()
        scheduler.executionCheck()
        scheduler.simulateStockPurchase()

        assertEquals(
            listOf(
                "sell",
                "recover-unknown",
                "reconcile",
                "simulate",
            ),
            calls,
        )
    }

    private fun scheduler(
        calls: MutableList<String>,
        gate: StockTradeScheduleGate,
    ): StockTradeScheduler {
        return StockTradeScheduler(
            createSellOrdersByStrategyUseCase = FakeCreateSellOrders(calls),
            reconcileExecutionsUseCase = FakeReconcileExecutions(calls),
            recoverUnknownOrderSubmissionsUseCase = FakeRecoverUnknownOrderSubmissions(calls),
            simulateStockPurchaseUseCase = FakeSimulateStockPurchase(calls),
            scheduleGate = gate,
        )
    }

    private class FakeCreateSellOrders(
        private val calls: MutableList<String>,
    ) : CreateSellOrdersByStrategyUseCase {
        override suspend fun execute() {
            calls += "sell"
        }
    }

    private class FakeReconcileExecutions(
        private val calls: MutableList<String>,
    ) : ReconcileExecutionsUseCase {
        override suspend fun execute() {
            calls += "reconcile"
        }
    }

    private class FakeRecoverUnknownOrderSubmissions(
        private val calls: MutableList<String>,
    ) : RecoverUnknownOrderSubmissionsUseCase {
        override suspend fun execute() {
            calls += "recover-unknown"
        }
    }

    private class FakeSimulateStockPurchase(
        private val calls: MutableList<String>,
    ) : SimulateStockPurchaseUseCase {
        override suspend fun execute() {
            calls += "simulate"
        }
    }
}

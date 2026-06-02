package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillCommand
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillStatus
import com.example.strategyexecutionservice.application.port.`in`.OrderFillKind
import com.example.strategyexecutionservice.application.port.out.FinalPriceBatingV1ExecutionState
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.MarketDataPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventRecord
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventType
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import com.example.strategyexecutionservice.application.port.out.StrategyMarketDataSnapshot
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplyOrderFillServiceTest {

    @Test
    fun `applies laor fill and completes strategy when auto restart is disabled`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            initial = LaorV4ExecutionState(
                executionId = "laor-v4:TQQQ",
                symbol = LaorV4StrategySymbol.TQQQ,
                totalSplitCount = 20,
                autoRestart = false,
                state = LaorV4StrategyState(
                    availableCash = 0.0,
                    holdingQuantity = 2,
                    averagePurchasePrice = 100.0,
                    progressRound = 1.0,
                ),
            ),
        )
        val orderEventPort = FakeStrategyExecutionOrderEventPort()
        val service = ApplyOrderFillService(statePort, FakeMarketDataPort(), orderEventPort)

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "fill-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.SELL,
                filledPrice = 120.0,
                filledQuantity = 2,
                orderTag = "TARGET_SELL",
                filledAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.APPLIED, result.status)
        assertEquals(StrategyExecutionOrderEventType.FILLED, orderEventPort.saved.single().type)
        with(statePort.saved.single()) {
            assertEquals(StrategyExecutionLifecycleStatus.COMPLETED, status)
            assertEquals(1, cycleNo)
            assertEquals(0L, state.holdingQuantity)
            assertEquals(240.0, state.availableCash)
        }
    }

    @Test
    fun `cycle close increments cycle number when auto restart is enabled`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            initial = LaorV4ExecutionState(
                executionId = "laor-v4:TQQQ",
                symbol = LaorV4StrategySymbol.TQQQ,
                totalSplitCount = 20,
                autoRestart = true,
                cycleNo = 2,
                state = LaorV4StrategyState(
                    availableCash = 0.0,
                    holdingQuantity = 1,
                    averagePurchasePrice = 100.0,
                    progressRound = 1.0,
                ),
            ),
        )
        val service = ApplyOrderFillService(statePort, FakeMarketDataPort(), FakeStrategyExecutionOrderEventPort())

        service.execute(
            ApplyOrderFillCommand(
                eventId = "fill-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.SELL,
                filledPrice = 120.0,
                filledQuantity = 1,
                orderTag = "TARGET_SELL",
                filledAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        with(statePort.saved.single()) {
            assertEquals(StrategyExecutionLifecycleStatus.ACTIVE, status)
            assertEquals(3, cycleNo)
        }
    }

    @Test
    fun `skips duplicate fill event without mutating strategy state`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            initial = LaorV4ExecutionState(
                executionId = "laor-v4:TQQQ",
                symbol = LaorV4StrategySymbol.TQQQ,
                totalSplitCount = 20,
                state = LaorV4StrategyState(
                    availableCash = 1000.0,
                    holdingQuantity = 1,
                    averagePurchasePrice = 100.0,
                    progressRound = 1.0,
                ),
            ),
        )
        val service = ApplyOrderFillService(
            statePort,
            FakeMarketDataPort(),
            FakeStrategyExecutionOrderEventPort(duplicateEventIds = setOf("fill-1")),
        )

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "fill-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.SELL,
                filledPrice = 120.0,
                filledQuantity = 1,
                orderTag = "TARGET_SELL",
                filledAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.SKIPPED_DUPLICATE, result.status)
        assertEquals(emptyList(), statePort.saved)
    }

    @Test
    fun `partial fill updates holdings without advancing laor progress round`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            initial = LaorV4ExecutionState(
                executionId = "laor-v4:TQQQ",
                symbol = LaorV4StrategySymbol.TQQQ,
                totalSplitCount = 20,
                state = LaorV4StrategyState(
                    availableCash = 2_000.0,
                    holdingQuantity = 10,
                    averagePurchasePrice = 100.0,
                    progressRound = 4.0,
                ),
            ),
        )
        val orderEventPort = FakeStrategyExecutionOrderEventPort()
        val service = ApplyOrderFillService(statePort, FakeMarketDataPort(), orderEventPort)

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "partial-fill-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.BUY,
                fillKind = OrderFillKind.PARTIALLY_FILLED,
                filledPrice = 90.0,
                filledQuantity = 5,
                orderTag = "STAR_HALF_BUY",
                filledAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.APPLIED, result.status)
        assertEquals(StrategyExecutionOrderEventType.PARTIALLY_FILLED, orderEventPort.saved.single().type)
        with(statePort.saved.single().state) {
            assertEquals(4.0, progressRound)
            assertEquals(15, holdingQuantity)
            assertEquals(1_550.0, availableCash)
        }
    }

    @Test
    fun `final price bating partial fill records active filled quantity`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            finalPriceInitial = finalPriceState(quantity = 3),
        )
        val orderEventPort = FakeStrategyExecutionOrderEventPort()
        val service = ApplyOrderFillService(statePort, FakeMarketDataPort(), orderEventPort)

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "final-price-partial-1",
                strategyExecutionId = "FinalPriceBatingV1:005930",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.BUY,
                fillKind = OrderFillKind.PARTIALLY_FILLED,
                filledPrice = 70_000.0,
                filledQuantity = 1,
                orderTag = "ENTRY_BUY",
                filledAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.APPLIED, result.status)
        assertEquals(StrategyExecutionOrderEventType.PARTIALLY_FILLED, orderEventPort.saved.single().type)
        with(statePort.finalPriceSaved.single()) {
            assertEquals(StrategyExecutionLifecycleStatus.ACTIVE, status)
            assertEquals(1, filledQuantity)
            assertEquals(70_000.0, averageFilledPrice)
            assertEquals(null, completedAt)
        }
    }

    @Test
    fun `final price bating full fill completes single shot strategy`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            finalPriceInitial = finalPriceState(quantity = 2, filledQuantity = 1, averageFilledPrice = 70_000.0),
        )
        val orderEventPort = FakeStrategyExecutionOrderEventPort()
        val service = ApplyOrderFillService(statePort, FakeMarketDataPort(), orderEventPort)

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "final-price-filled-1",
                strategyExecutionId = "FinalPriceBatingV1:005930",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.BUY,
                fillKind = OrderFillKind.FILLED,
                filledPrice = 72_000.0,
                filledQuantity = 1,
                orderTag = "ENTRY_BUY",
                filledAt = ZonedDateTime.parse("2026-06-02T09:30:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.APPLIED, result.status)
        assertEquals(StrategyExecutionOrderEventType.FILLED, orderEventPort.saved.single().type)
        with(statePort.finalPriceSaved.single()) {
            assertEquals(StrategyExecutionLifecycleStatus.COMPLETED, status)
            assertEquals(2, filledQuantity)
            assertEquals(71_000.0, averageFilledPrice)
            assertEquals(ZonedDateTime.parse("2026-06-02T09:30:00+09:00"), completedAt)
        }
    }

    @Test
    fun `final price bating overflow fill caps quantity and average calculation`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            finalPriceInitial = finalPriceState(quantity = 2, filledQuantity = 1, averageFilledPrice = 70_000.0),
        )
        val service = ApplyOrderFillService(statePort, FakeMarketDataPort(), FakeStrategyExecutionOrderEventPort())

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "final-price-overflow-1",
                strategyExecutionId = "FinalPriceBatingV1:005930",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.BUY,
                fillKind = OrderFillKind.PARTIALLY_FILLED,
                filledPrice = 72_000.0,
                filledQuantity = 2,
                orderTag = "ENTRY_BUY",
                filledAt = ZonedDateTime.parse("2026-06-02T09:35:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.APPLIED, result.status)
        with(statePort.finalPriceSaved.single()) {
            assertEquals(StrategyExecutionLifecycleStatus.COMPLETED, status)
            assertEquals(2, filledQuantity)
            assertEquals(71_000.0, averageFilledPrice)
            assertEquals(ZonedDateTime.parse("2026-06-02T09:35:00+09:00"), completedAt)
        }
    }

    private class FakeStrategyExecutionStatePort(
        private val initial: LaorV4ExecutionState? = null,
        private val finalPriceInitial: FinalPriceBatingV1ExecutionState? = null,
    ) : StrategyExecutionStatePort {
        val saved: MutableList<LaorV4ExecutionState> = mutableListOf()
        val finalPriceSaved: MutableList<FinalPriceBatingV1ExecutionState> = mutableListOf()

        override suspend fun tryMarkStartRequested(idempotencyKey: String): Boolean = true

        override suspend fun findLaorV4Strategy(executionId: String): LaorV4ExecutionState? {
            return initial?.takeIf { it.executionId == executionId }
        }

        override suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState> {
            return listOfNotNull(initial).filter { it.status == StrategyExecutionLifecycleStatus.ACTIVE }
        }

        override suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState) {
            saved += state
        }

        override suspend fun findFinalPriceBatingV1Strategy(executionId: String): FinalPriceBatingV1ExecutionState? {
            return finalPriceSaved.lastOrNull { it.executionId == executionId }
                ?: finalPriceInitial?.takeIf { it.executionId == executionId }
        }

        override suspend fun saveFinalPriceBatingV1Strategy(state: FinalPriceBatingV1ExecutionState) {
            finalPriceSaved += state
        }
    }

    private fun finalPriceState(
        quantity: Long,
        filledQuantity: Long = 0,
        averageFilledPrice: Double? = null,
    ): FinalPriceBatingV1ExecutionState {
        return FinalPriceBatingV1ExecutionState(
            executionId = "FinalPriceBatingV1:005930",
            symbol = "005930",
            market = "KRX",
            budget = 140_000.0,
            targetBuyPrice = 70_000.0,
            quantity = quantity,
            filledQuantity = filledQuantity,
            averageFilledPrice = averageFilledPrice,
            startedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
        )
    }

    private class FakeMarketDataPort : MarketDataPort {
        override suspend fun getMarketSnapshot(
            symbol: String,
            recentCloseCount: Int,
        ): StrategyMarketDataSnapshot {
            return StrategyMarketDataSnapshot(
                previousClose = 120.0,
                recentClosePrices = listOf(120.0, 119.0, 118.0, 117.0, 116.0),
            )
        }
    }

    private class FakeStrategyExecutionOrderEventPort(
        private val duplicateEventIds: Set<String> = emptySet(),
    ) : StrategyExecutionOrderEventPort {
        val saved: MutableList<StrategyExecutionOrderEventRecord> = mutableListOf()

        override suspend fun tryRecord(event: StrategyExecutionOrderEventRecord): Boolean {
            if (event.eventId in duplicateEventIds) return false

            saved += event
            return true
        }
    }
}

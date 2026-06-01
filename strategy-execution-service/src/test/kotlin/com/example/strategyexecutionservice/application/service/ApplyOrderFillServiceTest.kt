package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillCommand
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillStatus
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.MarketDataPort
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
        val service = ApplyOrderFillService(statePort, FakeMarketDataPort())

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
        val service = ApplyOrderFillService(statePort, FakeMarketDataPort())

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

    private class FakeStrategyExecutionStatePort(
        private val initial: LaorV4ExecutionState,
    ) : StrategyExecutionStatePort {
        val saved: MutableList<LaorV4ExecutionState> = mutableListOf()

        override suspend fun tryMarkStartRequested(idempotencyKey: String): Boolean = true

        override suspend fun findLaorV4Strategy(executionId: String): LaorV4ExecutionState? {
            return initial.takeIf { it.executionId == executionId }
        }

        override suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState> {
            return listOf(initial).filter { it.status == StrategyExecutionLifecycleStatus.ACTIVE }
        }

        override suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState) {
            saved += state
        }
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
}

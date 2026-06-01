package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.`in`.LaorV4State
import com.example.strategyexecutionservice.application.port.`in`.RunActiveStrategyExecutionsCommand
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.MarketDataPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import com.example.strategyexecutionservice.application.port.out.StrategyMarketDataSnapshot
import com.example.strategyexecutionservice.application.port.out.TradingCalendarPort
import com.example.strategyexecutionservice.application.port.out.TradingMarket
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RunActiveStrategyExecutionsServiceTest {

    @Test
    fun `runs active laor strategies from stored state and market data`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            activeStates = listOf(
                LaorV4ExecutionState(
                    executionId = "laor-v4-strategy:TQQQ",
                    symbol = LaorV4StrategySymbol.TQQQ,
                    totalSplitCount = 20,
                    firstBuyLimitMultiplier = 1.12,
                    state = LaorV4StrategyState(availableCash = 2_240.0),
                ),
            ),
        )
        val marketDataPort = FakeMarketDataPort()
        val runStrategyExecutionUseCase = FakeRunStrategyExecutionUseCase(
            plannedState = LaorV4State(
                mode = LaorV4StrategyMode.REVERSE,
                progressRound = 20.0,
                availableCash = 2_240.0,
                holdingQuantity = 10,
                averagePurchasePrice = 100.0,
            ),
        )
        val service = RunActiveStrategyExecutionsService(
            strategyExecutionStatePort = statePort,
            marketDataPort = marketDataPort,
            runStrategyExecutionUseCase = runStrategyExecutionUseCase,
            tradingCalendarPort = FakeTradingCalendarPort(),
        )
        val requestedAt = ZonedDateTime.parse("2026-05-31T09:00:00+09:00")

        val result = service.execute(
            RunActiveStrategyExecutionsCommand(
                executionRunId = "2026-05-31",
                requestedAt = requestedAt,
            ),
        )

        assertEquals("2026-05-31", result.executionRunId)
        assertEquals(1, result.activeStrategyCount)
        assertEquals(1, result.executedStrategyCount)
        assertEquals(3, result.createdOrderIntentCount)
        assertEquals("TQQQ" to 5, marketDataPort.requests.single())

        val command = runStrategyExecutionUseCase.commands.single() as RunStrategyExecutionCommand.LaorV4
        assertEquals("laor-v4-strategy:TQQQ", command.executionId)
        assertEquals("2026-05-31", command.executionRunId)
        assertEquals(LaorV4StrategySymbol.TQQQ, command.symbol)
        assertEquals(20, command.totalSplitCount)
        assertEquals(1.12, command.firstBuyLimitMultiplier)
        assertEquals(2_240.0, command.state.availableCash)
        assertEquals(100.0, command.market.previousClose)

        val savedState = statePort.saved.single()
        assertEquals("2026-05-31", savedState.lastExecutionRunId)
        assertEquals(requestedAt, savedState.lastExecutedAt)
        assertEquals(LaorV4StrategyMode.REVERSE, savedState.state.mode)
        assertEquals(20.0, savedState.state.progressRound)
        assertEquals(10L, savedState.state.holdingQuantity)
        assertEquals(100.0, savedState.state.averagePurchasePrice)
    }

    @Test
    fun `skips strategy that already ran for same execution run id`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            activeStates = listOf(
                LaorV4ExecutionState(
                    executionId = "laor-v4-strategy:TQQQ",
                    symbol = LaorV4StrategySymbol.TQQQ,
                    totalSplitCount = 20,
                    state = LaorV4StrategyState(availableCash = 2_240.0),
                    lastExecutionRunId = "ACTIVE_STRATEGIES_DAILY:2026-06-02",
                ),
            ),
        )
        val marketDataPort = FakeMarketDataPort()
        val runStrategyExecutionUseCase = FakeRunStrategyExecutionUseCase(
            plannedState = LaorV4State(availableCash = 2_240.0),
        )
        val service = RunActiveStrategyExecutionsService(
            strategyExecutionStatePort = statePort,
            marketDataPort = marketDataPort,
            runStrategyExecutionUseCase = runStrategyExecutionUseCase,
            tradingCalendarPort = FakeTradingCalendarPort(),
        )

        val result = service.execute(
            RunActiveStrategyExecutionsCommand(
                executionRunId = "ACTIVE_STRATEGIES_DAILY:2026-06-02",
                requestedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(1, result.activeStrategyCount)
        assertEquals(0, result.executedStrategyCount)
        assertEquals(0, result.createdOrderIntentCount)
        assertEquals(emptyList(), marketDataPort.requests)
        assertEquals(emptyList(), runStrategyExecutionUseCase.commands)
        assertEquals(emptyList(), statePort.saved)
    }

    @Test
    fun `skips daily execution when trading calendar is closed`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            activeStates = listOf(
                LaorV4ExecutionState(
                    executionId = "laor-v4-strategy:TQQQ",
                    symbol = LaorV4StrategySymbol.TQQQ,
                    totalSplitCount = 20,
                    state = LaorV4StrategyState(availableCash = 2_240.0),
                ),
            ),
        )
        val marketDataPort = FakeMarketDataPort()
        val runStrategyExecutionUseCase = FakeRunStrategyExecutionUseCase(
            plannedState = LaorV4State(availableCash = 2_240.0),
        )
        val service = RunActiveStrategyExecutionsService(
            strategyExecutionStatePort = statePort,
            marketDataPort = marketDataPort,
            runStrategyExecutionUseCase = runStrategyExecutionUseCase,
            tradingCalendarPort = FakeTradingCalendarPort(closedDates = setOf(LocalDate.parse("2026-07-03"))),
        )

        val result = service.execute(
            RunActiveStrategyExecutionsCommand(
                executionRunId = "ACTIVE_STRATEGIES_DAILY:2026-07-03",
                requestedAt = ZonedDateTime.parse("2026-07-03T09:00:00+09:00"),
            ),
        )

        assertEquals(1, result.activeStrategyCount)
        assertEquals(0, result.executedStrategyCount)
        assertEquals(0, result.createdOrderIntentCount)
        assertEquals("US market is closed on 2026-07-03", result.skippedReason)
        assertEquals(emptyList(), marketDataPort.requests)
        assertEquals(emptyList(), runStrategyExecutionUseCase.commands)
        assertEquals(emptyList(), statePort.saved)
    }

    private class FakeStrategyExecutionStatePort(
        private val activeStates: List<LaorV4ExecutionState>,
    ) : StrategyExecutionStatePort {
        val saved: MutableList<LaorV4ExecutionState> = mutableListOf()

        override suspend fun tryMarkStartRequested(idempotencyKey: String): Boolean {
            return true
        }

        override suspend fun findLaorV4Strategy(executionId: String): LaorV4ExecutionState? {
            return activeStates.firstOrNull { it.executionId == executionId }
        }

        override suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState> {
            return activeStates
        }

        override suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState) {
            saved += state
        }
    }

    private class FakeMarketDataPort : MarketDataPort {
        val requests: MutableList<Pair<String, Int>> = mutableListOf()

        override suspend fun getMarketSnapshot(
            symbol: String,
            recentCloseCount: Int,
        ): StrategyMarketDataSnapshot {
            requests += symbol to recentCloseCount
            return StrategyMarketDataSnapshot(
                previousClose = 100.0,
                recentClosePrices = listOf(99.0, 98.0, 97.0, 96.0, 95.0),
            )
        }
    }

    private class FakeTradingCalendarPort(
        private val closedDates: Set<LocalDate> = emptySet(),
    ) : TradingCalendarPort {
        override fun isTradingDay(market: TradingMarket, date: LocalDate): Boolean {
            return date !in closedDates
        }
    }

    private class FakeRunStrategyExecutionUseCase(
        private val plannedState: LaorV4State,
    ) : RunStrategyExecutionUseCase {
        val commands: MutableList<RunStrategyExecutionCommand> = mutableListOf()

        override suspend fun execute(command: RunStrategyExecutionCommand): RunStrategyExecutionResult {
            commands += command
            return RunStrategyExecutionResult(
                executionId = command.executionId,
                executionRunId = command.executionRunId,
                createdOrderIntentCount = 3,
                plannedState = plannedState,
            )
        }
    }
}

package com.example.strategyexecutionservice.adapter.`in`.temporal

import com.example.strategyexecutionservice.application.port.`in`.RunActiveStrategyExecutionsCommand
import com.example.strategyexecutionservice.application.port.`in`.RunActiveStrategyExecutionsResult
import com.example.strategyexecutionservice.application.port.`in`.RunActiveStrategyExecutionsUseCase
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionUseCase
import com.example.strategyexecutionservice.application.temporal.LaorV4StrategyWorkflowState
import com.example.strategyexecutionservice.application.temporal.RunActiveStrategyExecutionsWorkflowInput
import com.example.strategyexecutionservice.application.temporal.RunLaorV4StrategyWorkflowInput
import com.example.strategyexecutionservice.application.temporal.StrategyMarketWorkflowSnapshot
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyExecutionTemporalActivitiesAdapterTest {

    @Test
    fun `runs laor strategy use case from temporal activity input`() {
        val activeUseCase = FakeRunActiveStrategyExecutionsUseCase()
        val useCase = FakeRunStrategyExecutionUseCase()
        val adapter = StrategyExecutionTemporalActivitiesAdapter(activeUseCase, useCase)

        val result = adapter.runLaorV4Strategy(
            RunLaorV4StrategyWorkflowInput(
                executionId = "laor-v4-strategy:TQQQ",
                executionRunId = "2026-05-30",
                symbol = "TQQQ",
                totalSplitCount = 40,
                firstBuyLimitMultiplier = 1.12,
                state = LaorV4StrategyWorkflowState(
                    mode = "NORMAL",
                    progressRound = 3.5,
                    availableCash = 10_000.0,
                    holdingQuantity = 7,
                    averagePurchasePrice = 91.2,
                    realizedProfitLoss = 10.0,
                    reverseModeElapsedDays = 0,
                ),
                market = StrategyMarketWorkflowSnapshot(
                    previousClose = 100.0,
                    recentClosePrices = listOf(99.0, 98.0, 97.0, 96.0, 95.0),
                ),
            ),
        )

        assertEquals("laor-v4-strategy:TQQQ", result.executionId)
        assertEquals("2026-05-30", result.executionRunId)
        assertEquals(2, result.createdOrderIntentCount)

        val command = useCase.commands.single() as RunStrategyExecutionCommand.LaorV4
        assertEquals("laor-v4-strategy:TQQQ", command.executionId)
        assertEquals("2026-05-30", command.executionRunId)
        assertEquals(LaorV4StrategySymbol.TQQQ, command.symbol)
        assertEquals(40, command.totalSplitCount)
        assertEquals(1.12, command.firstBuyLimitMultiplier)
        assertEquals(LaorV4StrategyMode.NORMAL, command.state.mode)
        assertEquals(3.5, command.state.progressRound)
        assertEquals(10_000.0, command.state.availableCash)
        assertEquals(7, command.state.holdingQuantity)
        assertEquals(91.2, command.state.averagePurchasePrice)
        assertEquals(10.0, command.state.realizedProfitLoss)
        assertEquals(0, command.state.reverseModeElapsedDays)
        assertEquals(100.0, command.market.previousClose)
        assertEquals(listOf(99.0, 98.0, 97.0, 96.0, 95.0), command.market.recentClosePrices)
    }

    @Test
    fun `runs active strategy executions use case from temporal activity input`() {
        val activeUseCase = FakeRunActiveStrategyExecutionsUseCase()
        val useCase = FakeRunStrategyExecutionUseCase()
        val adapter = StrategyExecutionTemporalActivitiesAdapter(activeUseCase, useCase)

        val result = adapter.runActiveStrategyExecutions(
            RunActiveStrategyExecutionsWorkflowInput(
                executionRunId = "2026-05-31",
                requestedAt = "2026-05-31T09:00:00+09:00",
            ),
        )

        assertEquals("2026-05-31", result.executionRunId)
        assertEquals(2, result.activeStrategyCount)
        assertEquals(2, result.executedStrategyCount)
        assertEquals(4, result.createdOrderIntentCount)

        with(activeUseCase.commands.single()) {
            assertEquals("2026-05-31", executionRunId)
            assertEquals("2026-05-31T09:00+09:00", requestedAt.toString())
        }
    }

    private class FakeRunActiveStrategyExecutionsUseCase : RunActiveStrategyExecutionsUseCase {
        val commands: MutableList<RunActiveStrategyExecutionsCommand> = mutableListOf()

        override suspend fun execute(
            command: RunActiveStrategyExecutionsCommand,
        ): RunActiveStrategyExecutionsResult {
            commands += command
            return RunActiveStrategyExecutionsResult(
                executionRunId = command.executionRunId,
                activeStrategyCount = 2,
                executedStrategyCount = 2,
                createdOrderIntentCount = 4,
            )
        }
    }

    private class FakeRunStrategyExecutionUseCase : RunStrategyExecutionUseCase {
        val commands: MutableList<RunStrategyExecutionCommand> = mutableListOf()

        override suspend fun execute(command: RunStrategyExecutionCommand): RunStrategyExecutionResult {
            commands += command
            return RunStrategyExecutionResult(
                executionId = command.executionId,
                executionRunId = command.executionRunId,
                createdOrderIntentCount = 2,
            )
        }
    }
}

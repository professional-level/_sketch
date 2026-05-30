package com.example.strategyexecutionservice.adapter.`in`.temporal

import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionUseCase
import com.example.strategyexecutionservice.application.temporal.LaorV4StrategyWorkflowState
import com.example.strategyexecutionservice.application.temporal.RunLaorV4StrategyWorkflowInput
import com.example.strategyexecutionservice.application.temporal.StrategyMarketWorkflowSnapshot
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyExecutionTemporalActivitiesAdapterTest {

    @Test
    fun `runs laor strategy use case from temporal activity input`() {
        val useCase = FakeRunStrategyExecutionUseCase()
        val adapter = StrategyExecutionTemporalActivitiesAdapter(useCase)

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

package com.example.strategyexecutionservice.adapter.`in`.web

import com.example.strategyexecutionservice.application.port.`in`.LaorV4StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.QueryLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionStatus
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyExecutionControllerTest {

    @Test
    fun `registers laor v4 request with generated execution id`() = runBlocking {
        val useCase = FakeRegisterLaorV4StrategyExecutionUseCase()
        val controller = StrategyExecutionController(useCase, FakeQueryLaorV4StrategyExecutionUseCase())

        val response = controller.registerLaorV4StrategyExecution(
            RegisterLaorV4StrategyExecutionRequest(
                symbol = "tqqq",
                budget = 10_000.0,
                totalSplitCount = 20,
            ),
        )

        assertEquals("laor-v4:TQQQ", useCase.commands.single().executionId)
        assertEquals(LaorV4StrategySymbol.TQQQ, useCase.commands.single().symbol)
        assertEquals("REGISTERED", response.status)
        assertEquals("TQQQ", response.symbol)
    }

    @Test
    fun `gets laor v4 strategy execution state for operations`() = runBlocking {
        val execution = view(
            executionId = "laor-v4:TQQQ",
            progressRound = 3.5,
            availableCash = 1_200.0,
            holdingQuantity = 7,
            averagePurchasePrice = 101.2,
        )
        val controller = StrategyExecutionController(
            FakeRegisterLaorV4StrategyExecutionUseCase(),
            FakeQueryLaorV4StrategyExecutionUseCase(states = listOf(execution)),
        )

        val response = controller.getLaorV4StrategyExecution("laor-v4:TQQQ")

        assertEquals(200, response.statusCode.value())
        with(checkNotNull(response.body)) {
            assertEquals("laor-v4:TQQQ", executionId)
            assertEquals("TQQQ", symbol)
            assertEquals("ACTIVE", status)
            assertEquals(3.5, progressRound)
            assertEquals(1_200.0, availableCash)
            assertEquals(7, holdingQuantity)
            assertEquals(101.2, averagePurchasePrice)
        }
    }

    @Test
    fun `lists active laor v4 strategy execution states`() = runBlocking {
        val controller = StrategyExecutionController(
            FakeRegisterLaorV4StrategyExecutionUseCase(),
            FakeQueryLaorV4StrategyExecutionUseCase(
                states = listOf(
                    view(executionId = "laor-v4:TQQQ"),
                    view(executionId = "laor-v4:SOXL", symbol = LaorV4StrategySymbol.SOXL),
                ),
            ),
        )

        val response = controller.getActiveLaorV4StrategyExecutions()

        assertEquals(listOf("laor-v4:TQQQ", "laor-v4:SOXL"), response.map { it.executionId })
    }

    private class FakeRegisterLaorV4StrategyExecutionUseCase : RegisterLaorV4StrategyExecutionUseCase {
        val commands: MutableList<RegisterLaorV4StrategyExecutionCommand> = mutableListOf()

        override suspend fun execute(
            command: RegisterLaorV4StrategyExecutionCommand,
        ): RegisterLaorV4StrategyExecutionResult {
            commands += command
            return RegisterLaorV4StrategyExecutionResult(
                executionId = command.executionId,
                status = RegisterLaorV4StrategyExecutionStatus.REGISTERED,
                symbol = command.symbol,
                budget = command.budget,
                totalSplitCount = command.totalSplitCount,
                firstBuyLimitMultiplier = command.firstBuyLimitMultiplier,
                autoRestart = command.autoRestart,
            )
        }
    }

    private class FakeQueryLaorV4StrategyExecutionUseCase(
        private val states: List<LaorV4StrategyExecutionView> = emptyList(),
    ) : QueryLaorV4StrategyExecutionUseCase {
        override suspend fun find(executionId: String): LaorV4StrategyExecutionView? {
            return states.firstOrNull { it.executionId == executionId }
        }

        override suspend fun findActive(): List<LaorV4StrategyExecutionView> {
            return states
        }
    }

    private fun view(
        executionId: String,
        symbol: LaorV4StrategySymbol = LaorV4StrategySymbol.TQQQ,
        progressRound: Double = 1.0,
        availableCash: Double = 10_000.0,
        holdingQuantity: Long = 0,
        averagePurchasePrice: Double = 0.0,
    ): LaorV4StrategyExecutionView {
        return LaorV4StrategyExecutionView(
            executionId = executionId,
            symbol = symbol,
            status = StrategyExecutionLifecycleStatus.ACTIVE,
            cycleNo = 1,
            totalSplitCount = 20,
            firstBuyLimitMultiplier = 1.12,
            autoRestart = true,
            mode = LaorV4StrategyMode.NORMAL,
            progressRound = progressRound,
            availableCash = availableCash,
            holdingQuantity = holdingQuantity,
            averagePurchasePrice = averagePurchasePrice,
            realizedProfitLoss = 0.0,
            reverseModeElapsedDays = 0,
            lastExecutionRunId = "ACTIVE_STRATEGIES_DAILY:2026-06-02",
            lastExecutedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
        )
    }
}

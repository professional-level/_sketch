package com.example.strategyexecutionservice.adapter.`in`.web

import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionStatus
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyExecutionControllerTest {

    @Test
    fun `registers laor v4 request with generated execution id`() = runBlocking {
        val useCase = FakeRegisterLaorV4StrategyExecutionUseCase()
        val controller = StrategyExecutionController(useCase)

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
}

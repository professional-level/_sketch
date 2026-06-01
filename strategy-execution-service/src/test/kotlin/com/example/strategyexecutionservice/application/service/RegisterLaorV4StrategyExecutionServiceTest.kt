package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionStatus
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterLaorV4StrategyExecutionServiceTest {

    @Test
    fun `registers laor v4 strategy execution with initial cash state`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort()
        val service = RegisterLaorV4StrategyExecutionService(statePort)

        val result = service.execute(
            RegisterLaorV4StrategyExecutionCommand(
                executionId = "laor-v4:TQQQ",
                symbol = LaorV4StrategySymbol.TQQQ,
                budget = 10_000.0,
                totalSplitCount = 20,
            ),
        )

        assertEquals(RegisterLaorV4StrategyExecutionStatus.REGISTERED, result.status)
        assertEquals("laor-v4:TQQQ", result.executionId)
        with(statePort.states.single()) {
            assertEquals(LaorV4StrategySymbol.TQQQ, symbol)
            assertEquals(20, totalSplitCount)
            assertEquals(10_000.0, state.availableCash)
            assertEquals(0L, state.holdingQuantity)
            assertEquals(true, autoRestart)
        }
    }

    @Test
    fun `does not overwrite existing laor v4 strategy execution`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            initialStates = listOf(
                LaorV4ExecutionState(
                    executionId = "laor-v4:TQQQ",
                    symbol = LaorV4StrategySymbol.TQQQ,
                    totalSplitCount = 20,
                    state = LaorV4StrategyState(availableCash = 1_000.0),
                ),
            ),
        )
        val service = RegisterLaorV4StrategyExecutionService(statePort)

        val result = service.execute(
            RegisterLaorV4StrategyExecutionCommand(
                executionId = "laor-v4:TQQQ",
                symbol = LaorV4StrategySymbol.TQQQ,
                budget = 10_000.0,
                totalSplitCount = 40,
            ),
        )

        assertEquals(RegisterLaorV4StrategyExecutionStatus.ALREADY_REGISTERED, result.status)
        assertEquals(1_000.0, result.budget)
        assertEquals(20, result.totalSplitCount)
        assertEquals(0, statePort.saveCount)
    }

    private class FakeStrategyExecutionStatePort(
        initialStates: List<LaorV4ExecutionState> = emptyList(),
    ) : StrategyExecutionStatePort {
        val states: MutableList<LaorV4ExecutionState> = initialStates.toMutableList()
        var saveCount: Int = 0
        private val processedStartRequests: MutableSet<String> = mutableSetOf()

        override suspend fun tryMarkStartRequested(idempotencyKey: String): Boolean {
            return processedStartRequests.add(idempotencyKey)
        }

        override suspend fun findLaorV4Strategy(executionId: String): LaorV4ExecutionState? {
            return states.firstOrNull { it.executionId == executionId }
        }

        override suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState> {
            return states
        }

        override suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState) {
            states.removeAll { it.executionId == state.executionId }
            states += state
            saveCount += 1
        }
    }
}

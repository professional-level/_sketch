package com.example.strategyexecutionservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import java.util.concurrent.ConcurrentHashMap

@PersistenceAdapter
internal class InMemoryStrategyExecutionStateAdapter : StrategyExecutionStatePort {
    private val laorV4States: MutableMap<String, LaorV4ExecutionState> = ConcurrentHashMap()
    private val processedStartRequests: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override suspend fun tryMarkStartRequested(idempotencyKey: String): Boolean {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        return processedStartRequests.add(idempotencyKey)
    }

    override suspend fun findLaorV4Strategy(executionId: String): LaorV4ExecutionState? {
        return laorV4States[executionId]
    }

    override suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState> {
        return laorV4States.values.filter { it.status == StrategyExecutionLifecycleStatus.ACTIVE }
    }

    override suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState) {
        laorV4States[state.executionId] = state
    }
}

package com.example.strategyexecutionservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import java.util.concurrent.ConcurrentHashMap

@PersistenceAdapter
internal class InMemoryStrategyExecutionStateAdapter : StrategyExecutionStatePort {
    private val laorV4States: MutableMap<String, LaorV4ExecutionState> = ConcurrentHashMap()

    override suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState> {
        return laorV4States.values.toList()
    }

    override suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState) {
        laorV4States[state.executionId] = state
    }
}

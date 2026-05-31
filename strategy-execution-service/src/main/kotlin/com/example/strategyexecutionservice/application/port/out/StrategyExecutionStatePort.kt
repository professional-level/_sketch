package com.example.strategyexecutionservice.application.port.out

import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.ZonedDateTime

interface StrategyExecutionStatePort {
    suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState>
    suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState)
}

data class LaorV4ExecutionState(
    val executionId: String,
    val symbol: LaorV4StrategySymbol,
    val totalSplitCount: Int,
    val firstBuyLimitMultiplier: Double = LaorV4StrategyConfig.DEFAULT_FIRST_BUY_LIMIT_MULTIPLIER,
    val state: LaorV4StrategyState,
    val lastExecutionRunId: String? = null,
    val lastExecutedAt: ZonedDateTime? = null,
) {
    init {
        require(executionId.isNotBlank()) { "executionId must not be blank" }
        require(totalSplitCount > 1) { "totalSplitCount must be greater than 1" }
        require(firstBuyLimitMultiplier > 1.0) { "firstBuyLimitMultiplier must be greater than 1" }
    }
}

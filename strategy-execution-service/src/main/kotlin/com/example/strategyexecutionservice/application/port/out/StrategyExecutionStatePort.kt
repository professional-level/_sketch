package com.example.strategyexecutionservice.application.port.out

import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.ZonedDateTime

interface StrategyExecutionStatePort {
    suspend fun tryMarkStartRequested(idempotencyKey: String): Boolean
    suspend fun findLaorV4Strategy(executionId: String): LaorV4ExecutionState?
    suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState>
    suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState)
    suspend fun findFinalPriceBatingV1Strategy(executionId: String): FinalPriceBatingV1ExecutionState?
    suspend fun findActiveFinalPriceBatingV1Strategies(): List<FinalPriceBatingV1ExecutionState> = emptyList()
    suspend fun saveFinalPriceBatingV1Strategy(state: FinalPriceBatingV1ExecutionState)
}

data class FinalPriceBatingV1ExecutionState(
    val executionId: String,
    val symbol: String,
    val market: String,
    val budget: Double,
    val targetBuyPrice: Double,
    val quantity: Long,
    val status: StrategyExecutionLifecycleStatus = StrategyExecutionLifecycleStatus.ACTIVE,
    val filledQuantity: Long = 0,
    val averageFilledPrice: Double? = null,
    val sellTargetPrice: Double? = null,
    val sellQuantity: Long = 0,
    val sellIntentCreatedAt: ZonedDateTime? = null,
    val soldQuantity: Long = 0,
    val averageSoldPrice: Double? = null,
    val startedAt: ZonedDateTime,
    val completedAt: ZonedDateTime? = null,
) {
    init {
        require(executionId.isNotBlank()) { "executionId must not be blank" }
        require(symbol.isNotBlank()) { "symbol must not be blank" }
        require(market.isNotBlank()) { "market must not be blank" }
        require(budget > 0.0) { "budget must be positive" }
        require(targetBuyPrice > 0.0) { "targetBuyPrice must be positive" }
        require(quantity >= 0) { "quantity must not be negative" }
        require(filledQuantity >= 0) { "filledQuantity must not be negative" }
        require(sellTargetPrice == null || sellTargetPrice > 0.0) { "sellTargetPrice must be positive" }
        require(sellQuantity >= 0) { "sellQuantity must not be negative" }
        require(soldQuantity >= 0) { "soldQuantity must not be negative" }
    }
}

data class LaorV4ExecutionState(
    val executionId: String,
    val symbol: LaorV4StrategySymbol,
    val totalSplitCount: Int,
    val firstBuyLimitMultiplier: Double = LaorV4StrategyConfig.DEFAULT_FIRST_BUY_LIMIT_MULTIPLIER,
    val autoRestart: Boolean = true,
    val cycleNo: Int = 1,
    val status: StrategyExecutionLifecycleStatus = StrategyExecutionLifecycleStatus.ACTIVE,
    val state: LaorV4StrategyState,
    val lastExecutionRunId: String? = null,
    val lastExecutedAt: ZonedDateTime? = null,
) {
    init {
        require(executionId.isNotBlank()) { "executionId must not be blank" }
        require(totalSplitCount > 1) { "totalSplitCount must be greater than 1" }
        require(firstBuyLimitMultiplier > 1.0) { "firstBuyLimitMultiplier must be greater than 1" }
        require(cycleNo > 0) { "cycleNo must be positive" }
    }
}

enum class StrategyExecutionLifecycleStatus {
    ACTIVE,
    COMPLETED,
}

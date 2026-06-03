package com.example.strategyexecutionservice.application.port.`in`

import com.example.common.UseCase
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.ZonedDateTime

@UseCase
interface QueryLaorV4StrategyExecutionUseCase {
    suspend fun find(executionId: String): LaorV4StrategyExecutionView?
    suspend fun findActive(): List<LaorV4StrategyExecutionView>
}

data class LaorV4StrategyExecutionView(
    val executionId: String,
    val symbol: LaorV4StrategySymbol,
    val status: StrategyExecutionLifecycleStatus,
    val cycleNo: Int,
    val totalSplitCount: Int,
    val firstBuyLimitPercentAbovePreviousClose: Double,
    val autoRestart: Boolean,
    val mode: LaorV4StrategyMode,
    val progressRound: Double,
    val availableCash: Double,
    val holdingQuantity: Long,
    val averagePurchasePrice: Double,
    val realizedProfitLoss: Double,
    val reverseModeElapsedDays: Int,
    val lastExecutionRunId: String?,
    val lastExecutedAt: ZonedDateTime?,
)

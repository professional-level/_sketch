package com.example.strategyexecutionservice.application.port.`in`

import com.example.common.UseCase
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol

@UseCase
interface RunStrategyExecutionUseCase {
    suspend fun execute(command: RunStrategyExecutionCommand): RunStrategyExecutionResult
}

sealed class RunStrategyExecutionCommand {
    abstract val executionId: String
    abstract val executionRunId: String

    data class LaorV4(
        override val executionId: String,
        override val executionRunId: String,
        val symbol: LaorV4StrategySymbol,
        val totalSplitCount: Int,
        val firstBuyLimitMultiplier: Double,
        val state: LaorV4State,
        val market: MarketSnapshot,
    ) : RunStrategyExecutionCommand()
}

data class LaorV4State(
    val mode: LaorV4StrategyMode = LaorV4StrategyMode.NORMAL,
    val progressRound: Double = 0.0,
    val availableCash: Double,
    val holdingQuantity: Long = 0,
    val averagePurchasePrice: Double = 0.0,
    val realizedProfitLoss: Double = 0.0,
    val reverseModeElapsedDays: Int = 0,
)

data class MarketSnapshot(
    val previousClose: Double,
    val recentClosePrices: List<Double> = emptyList(),
)

data class RunStrategyExecutionResult(
    val executionId: String,
    val executionRunId: String,
    val createdOrderIntentCount: Int,
    val plannedState: LaorV4State? = null,
)

package com.example.strategyexecutionservice.application.temporal

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod

@ActivityInterface
interface StrategyExecutionTemporalActivities {
    @ActivityMethod
    fun runLaorV4Strategy(input: RunLaorV4StrategyWorkflowInput): RunLaorV4StrategyWorkflowResult
}

data class RunLaorV4StrategyWorkflowInput(
    val executionId: String = "",
    val executionRunId: String = "",
    val symbol: String = "",
    val totalSplitCount: Int = 20,
    val firstBuyLimitMultiplier: Double = 1.12,
    val state: LaorV4StrategyWorkflowState = LaorV4StrategyWorkflowState(),
    val market: StrategyMarketWorkflowSnapshot = StrategyMarketWorkflowSnapshot(),
)

data class LaorV4StrategyWorkflowState(
    val mode: String = "NORMAL",
    val progressRound: Double = 0.0,
    val availableCash: Double = 0.0,
    val holdingQuantity: Long = 0,
    val averagePurchasePrice: Double = 0.0,
    val realizedProfitLoss: Double = 0.0,
    val reverseModeElapsedDays: Int = 0,
)

data class StrategyMarketWorkflowSnapshot(
    val previousClose: Double = 0.0,
    val recentClosePrices: List<Double> = emptyList(),
)

data class RunLaorV4StrategyWorkflowResult(
    val executionId: String = "",
    val executionRunId: String = "",
    val createdOrderIntentCount: Int = 0,
)

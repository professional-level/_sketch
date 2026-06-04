package com.example.strategyexecutionservice.application.temporal

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod

@ActivityInterface
interface StrategyExecutionTemporalActivities {
    @ActivityMethod
    fun runLaorV4Strategy(input: RunLaorV4StrategyWorkflowInput): RunLaorV4StrategyWorkflowResult

    @ActivityMethod
    fun runActiveStrategyExecutions(
        input: RunActiveStrategyExecutionsWorkflowInput,
    ): RunActiveStrategyExecutionsWorkflowResult
}

const val LAOR_ORDER_MILESTONE_SIGNAL_NAME = "OnLaorOrderMilestone"

data class RunActiveStrategyExecutionsWorkflowInput(
    val executionRunId: String = "",
    val requestedAt: String = "",
    val executionRunIdPrefix: String = "ACTIVE_STRATEGIES_DAILY",
    val timeZone: String = "Asia/Seoul",
    val traceContext: TemporalTraceContext = TemporalTraceContext(),
)

data class RunActiveStrategyExecutionsWorkflowResult(
    val executionRunId: String = "",
    val activeStrategyCount: Int = 0,
    val executedStrategyCount: Int = 0,
    val createdOrderIntentCount: Int = 0,
    val skippedReason: String? = null,
)

data class RunLaorV4StrategyWorkflowInput(
    val executionId: String = "",
    val executionRunId: String = "",
    val requestedAt: String = "",
    val symbol: String = "",
    val totalSplitCount: Int = 0,
    val firstBuyLimitPercentAbovePreviousClose: Double = 0.0,
    val state: LaorV4StrategyWorkflowState = LaorV4StrategyWorkflowState(),
    val market: StrategyMarketWorkflowSnapshot = StrategyMarketWorkflowSnapshot(),
    val traceContext: TemporalTraceContext = TemporalTraceContext(),
)

data class TemporalTraceContext(
    val traceId: String = "",
    val spanId: String = "",
    val traceParent: String = "",
) {
    fun isEmpty(): Boolean {
        return traceId.isBlank() && spanId.isBlank() && traceParent.isBlank()
    }
}

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

data class LaorOrderMilestoneWorkflowSignal(
    val eventId: String = "",
    val milestoneType: String = "",
    val strategyExecutionId: String = "",
    val orderIntentId: String = "",
    val brokerOrderId: String? = null,
    val orderTag: String? = null,
    val side: String? = null,
    val filledQuantity: Long = 0,
    val averageFilledPrice: Double? = null,
    val occurredAt: String = "",
    val sourceEventIds: List<String> = emptyList(),
    val idempotencyKey: String = "",
)

data class RunLaorV4StrategyWorkflowResult(
    val executionId: String = "",
    val executionRunId: String = "",
    val createdOrderIntentCount: Int = 0,
    val skippedReason: String? = null,
)

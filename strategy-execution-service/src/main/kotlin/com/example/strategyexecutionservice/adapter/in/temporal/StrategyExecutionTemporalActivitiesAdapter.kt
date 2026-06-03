package com.example.strategyexecutionservice.adapter.`in`.temporal

import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.`in`.LaorV4State
import com.example.strategyexecutionservice.application.port.`in`.MarketSnapshot
import com.example.strategyexecutionservice.application.port.`in`.RunActiveStrategyExecutionsCommand
import com.example.strategyexecutionservice.application.port.`in`.RunActiveStrategyExecutionsUseCase
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionUseCase
import com.example.strategyexecutionservice.application.temporal.LaorV4StrategyWorkflowState
import com.example.strategyexecutionservice.application.temporal.RunActiveStrategyExecutionsWorkflowInput
import com.example.strategyexecutionservice.application.temporal.RunActiveStrategyExecutionsWorkflowResult
import com.example.strategyexecutionservice.application.temporal.RunLaorV4StrategyWorkflowInput
import com.example.strategyexecutionservice.application.temporal.RunLaorV4StrategyWorkflowResult
import com.example.strategyexecutionservice.application.temporal.StrategyExecutionTemporalActivities
import com.example.strategyexecutionservice.application.temporal.StrategyMarketWorkflowSnapshot
import com.example.strategyexecutionservice.application.temporal.TemporalTraceContext
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import common.observability.TraceContext
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking

@ExternalApiAdapter
class StrategyExecutionTemporalActivitiesAdapter(
    private val runActiveStrategyExecutionsUseCase: RunActiveStrategyExecutionsUseCase,
    private val runStrategyExecutionUseCase: RunStrategyExecutionUseCase,
) : StrategyExecutionTemporalActivities {

    override fun runActiveStrategyExecutions(
        input: RunActiveStrategyExecutionsWorkflowInput,
    ): RunActiveStrategyExecutionsWorkflowResult {
        return runBlocking {
            input.traceContext.withMdc {
                val result = runActiveStrategyExecutionsUseCase.execute(input.toCommand())
                RunActiveStrategyExecutionsWorkflowResult(
                    executionRunId = result.executionRunId,
                    activeStrategyCount = result.activeStrategyCount,
                    executedStrategyCount = result.executedStrategyCount,
                    createdOrderIntentCount = result.createdOrderIntentCount,
                    skippedReason = result.skippedReason,
                )
            }
        }
    }

    override fun runLaorV4Strategy(input: RunLaorV4StrategyWorkflowInput): RunLaorV4StrategyWorkflowResult {
        return runBlocking {
            input.traceContext.withMdc {
                val result = runStrategyExecutionUseCase.execute(input.toCommand())
                RunLaorV4StrategyWorkflowResult(
                    executionId = result.executionId,
                    executionRunId = result.executionRunId,
                    createdOrderIntentCount = result.createdOrderIntentCount,
                    skippedReason = result.skippedReason,
                )
            }
        }
    }

    private fun RunActiveStrategyExecutionsWorkflowInput.toCommand(): RunActiveStrategyExecutionsCommand {
        val requestedAt = requestedAt
            .takeIf { it.isNotBlank() }
            ?.let(ZonedDateTime::parse)
            ?: ZonedDateTime.now(ZoneId.of(timeZone))
        val executionRunId = executionRunId
            .takeIf { it.isNotBlank() }
            ?: "$executionRunIdPrefix:${requestedAt.toLocalDate()}"
        return RunActiveStrategyExecutionsCommand(
            executionRunId = executionRunId,
            requestedAt = requestedAt,
        )
    }

    private fun RunLaorV4StrategyWorkflowInput.toCommand(): RunStrategyExecutionCommand.LaorV4 {
        val requestedAt = this.requestedAt
            .takeIf { it.isNotBlank() }
            ?.let(ZonedDateTime::parse)
            ?: ZonedDateTime.now()
        return RunStrategyExecutionCommand.LaorV4(
            executionId = executionId,
            executionRunId = executionRunId,
            requestedAt = requestedAt,
            symbol = LaorV4StrategySymbol.valueOf(symbol),
            totalSplitCount = totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = firstBuyLimitPercentAbovePreviousClose,
            state = state.toCommandState(),
            market = market.toCommandMarket(),
        )
    }

    private fun LaorV4StrategyWorkflowState.toCommandState(): LaorV4State {
        return LaorV4State(
            mode = LaorV4StrategyMode.valueOf(mode),
            progressRound = progressRound,
            availableCash = availableCash,
            holdingQuantity = holdingQuantity,
            averagePurchasePrice = averagePurchasePrice,
            realizedProfitLoss = realizedProfitLoss,
            reverseModeElapsedDays = reverseModeElapsedDays,
        )
    }

    private fun StrategyMarketWorkflowSnapshot.toCommandMarket(): MarketSnapshot {
        return MarketSnapshot(
            previousClose = previousClose,
            recentClosePrices = recentClosePrices,
        )
    }
}

private suspend fun <T> TemporalTraceContext.withMdc(block: suspend () -> T): T {
    val context = TraceContext.fromHeaders(
        traceParent = traceParent,
        traceId = traceId,
        spanId = spanId,
    )
    return if (context.isEmpty()) {
        block()
    } else {
        context.withMdc(block)
    }
}

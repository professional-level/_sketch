package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.LaorV4State
import com.example.strategyexecutionservice.application.port.`in`.MarketSnapshot
import com.example.strategyexecutionservice.application.port.`in`.RunActiveStrategyExecutionsCommand
import com.example.strategyexecutionservice.application.port.`in`.RunActiveStrategyExecutionsResult
import com.example.strategyexecutionservice.application.port.`in`.RunActiveStrategyExecutionsUseCase
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.MarketDataPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import com.example.strategyexecutionservice.application.port.out.StrategyMarketDataSnapshot
import com.example.strategyexecutionservice.application.port.out.TradingCalendarPort
import com.example.strategyexecutionservice.application.port.out.TradingMarket
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import java.time.ZonedDateTime

@UseCaseImpl
class RunActiveStrategyExecutionsService(
    private val strategyExecutionStatePort: StrategyExecutionStatePort,
    private val marketDataPort: MarketDataPort,
    private val runStrategyExecutionUseCase: RunStrategyExecutionUseCase,
    private val tradingCalendarPort: TradingCalendarPort,
) : RunActiveStrategyExecutionsUseCase {

    override suspend fun execute(command: RunActiveStrategyExecutionsCommand): RunActiveStrategyExecutionsResult {
        val activeStrategies = strategyExecutionStatePort.findActiveLaorV4Strategies()
        val requestedDate = tradingCalendarPort.tradingDate(TradingMarket.US, command.requestedAt)
        if (!tradingCalendarPort.isTradingDay(TradingMarket.US, requestedDate)) {
            return RunActiveStrategyExecutionsResult(
                executionRunId = command.executionRunId,
                activeStrategyCount = activeStrategies.size,
                executedStrategyCount = 0,
                createdOrderIntentCount = 0,
                skippedReason = "US market is closed on $requestedDate",
            )
        }

        var executedStrategyCount = 0
        var createdOrderIntentCount = 0
        val orderRequestedAt = tradingCalendarPort.orderSessionStartAt(TradingMarket.US, command.requestedAt)
        if (orderRequestedAt.toInstant().isAfter(command.requestedAt.toInstant())) {
            return RunActiveStrategyExecutionsResult(
                executionRunId = command.executionRunId,
                activeStrategyCount = activeStrategies.size,
                executedStrategyCount = 0,
                createdOrderIntentCount = 0,
                skippedReason = "US market order session is not open until $orderRequestedAt",
            )
        }

        for (strategy in activeStrategies) {
            if (strategy.lastExecutionRunId == command.executionRunId) {
                continue
            }
            val market = marketDataPort.getMarketSnapshot(
                symbol = strategy.symbol.ticker,
                recentCloseCount = RECENT_CLOSE_COUNT,
            )
            val result = runStrategyExecutionUseCase.execute(
                strategy.toCommand(
                    executionRunId = command.executionRunId,
                    requestedAt = orderRequestedAt,
                    market = market,
                ),
            )

            strategyExecutionStatePort.saveLaorV4Strategy(
                strategy.copy(
                    state = result.plannedState?.toDomain() ?: strategy.state,
                    lastExecutionRunId = command.executionRunId,
                    lastExecutedAt = command.requestedAt,
                ),
            )
            executedStrategyCount += 1
            createdOrderIntentCount += result.createdOrderIntentCount
        }

        return RunActiveStrategyExecutionsResult(
            executionRunId = command.executionRunId,
            activeStrategyCount = activeStrategies.size,
            executedStrategyCount = executedStrategyCount,
            createdOrderIntentCount = createdOrderIntentCount,
        )
    }

    private fun LaorV4ExecutionState.toCommand(
        executionRunId: String,
        requestedAt: ZonedDateTime,
        market: StrategyMarketDataSnapshot,
    ): RunStrategyExecutionCommand.LaorV4 {
        return RunStrategyExecutionCommand.LaorV4(
            executionId = executionId,
            executionRunId = executionRunId,
            requestedAt = requestedAt,
            symbol = symbol,
            totalSplitCount = totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = firstBuyLimitPercentAbovePreviousClose,
            state = state.toApplication(),
            market = market.toApplication(),
        )
    }

    private fun LaorV4StrategyState.toApplication(): LaorV4State {
        return LaorV4State(
            mode = mode,
            progressRound = progressRound,
            availableCash = availableCash,
            holdingQuantity = holdingQuantity,
            averagePurchasePrice = averagePurchasePrice,
            realizedProfitLoss = realizedProfitLoss,
            reverseModeElapsedDays = reverseModeElapsedDays,
        )
    }

    private fun LaorV4State.toDomain(): LaorV4StrategyState {
        return LaorV4StrategyState(
            mode = mode,
            progressRound = progressRound,
            availableCash = availableCash,
            holdingQuantity = holdingQuantity,
            averagePurchasePrice = averagePurchasePrice,
            realizedProfitLoss = realizedProfitLoss,
            reverseModeElapsedDays = reverseModeElapsedDays,
        )
    }

    private fun StrategyMarketDataSnapshot.toApplication(): MarketSnapshot {
        return MarketSnapshot(
            previousClose = previousClose,
            recentClosePrices = recentClosePrices,
        )
    }

    companion object {
        private const val RECENT_CLOSE_COUNT = 5
    }
}

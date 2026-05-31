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
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState

@UseCaseImpl
class RunActiveStrategyExecutionsService(
    private val strategyExecutionStatePort: StrategyExecutionStatePort,
    private val marketDataPort: MarketDataPort,
    private val runStrategyExecutionUseCase: RunStrategyExecutionUseCase,
) : RunActiveStrategyExecutionsUseCase {

    override suspend fun execute(command: RunActiveStrategyExecutionsCommand): RunActiveStrategyExecutionsResult {
        val activeStrategies = strategyExecutionStatePort.findActiveLaorV4Strategies()
        var executedStrategyCount = 0
        var createdOrderIntentCount = 0

        for (strategy in activeStrategies) {
            val market = marketDataPort.getMarketSnapshot(
                symbol = strategy.symbol.ticker,
                recentCloseCount = RECENT_CLOSE_COUNT,
            )
            val result = runStrategyExecutionUseCase.execute(
                strategy.toCommand(
                    executionRunId = command.executionRunId,
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
        market: StrategyMarketDataSnapshot,
    ): RunStrategyExecutionCommand.LaorV4 {
        return RunStrategyExecutionCommand.LaorV4(
            executionId = executionId,
            executionRunId = executionRunId,
            symbol = symbol,
            totalSplitCount = totalSplitCount,
            firstBuyLimitMultiplier = firstBuyLimitMultiplier,
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

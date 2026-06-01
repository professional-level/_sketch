package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.LaorV4StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.QueryLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort

@UseCaseImpl
class QueryLaorV4StrategyExecutionService(
    private val strategyExecutionStatePort: StrategyExecutionStatePort,
) : QueryLaorV4StrategyExecutionUseCase {

    override suspend fun find(executionId: String): LaorV4StrategyExecutionView? {
        return strategyExecutionStatePort.findLaorV4Strategy(executionId)?.toView()
    }

    override suspend fun findActive(): List<LaorV4StrategyExecutionView> {
        return strategyExecutionStatePort.findActiveLaorV4Strategies().map { it.toView() }
    }

    private fun LaorV4ExecutionState.toView(): LaorV4StrategyExecutionView {
        return LaorV4StrategyExecutionView(
            executionId = executionId,
            symbol = symbol,
            status = status,
            cycleNo = cycleNo,
            totalSplitCount = totalSplitCount,
            firstBuyLimitMultiplier = firstBuyLimitMultiplier,
            autoRestart = autoRestart,
            mode = state.mode,
            progressRound = state.progressRound,
            availableCash = state.availableCash,
            holdingQuantity = state.holdingQuantity,
            averagePurchasePrice = state.averagePurchasePrice,
            realizedProfitLoss = state.realizedProfitLoss,
            reverseModeElapsedDays = state.reverseModeElapsedDays,
            lastExecutionRunId = lastExecutionRunId,
            lastExecutedAt = lastExecutedAt,
        )
    }
}

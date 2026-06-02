package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.FinalPriceBatingV1StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.QueryFinalPriceBatingV1StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.out.FinalPriceBatingV1ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort

@UseCaseImpl
class QueryFinalPriceBatingV1StrategyExecutionService(
    private val strategyExecutionStatePort: StrategyExecutionStatePort,
) : QueryFinalPriceBatingV1StrategyExecutionUseCase {

    override suspend fun find(executionId: String): FinalPriceBatingV1StrategyExecutionView? {
        return strategyExecutionStatePort.findFinalPriceBatingV1Strategy(executionId)?.toView()
    }

    override suspend fun findActive(): List<FinalPriceBatingV1StrategyExecutionView> {
        return strategyExecutionStatePort.findActiveFinalPriceBatingV1Strategies().map { it.toView() }
    }

    private fun FinalPriceBatingV1ExecutionState.toView(): FinalPriceBatingV1StrategyExecutionView {
        val currentHoldingQuantity = (filledQuantity - soldQuantity).coerceAtLeast(0L)
        val filledCost = (averageFilledPrice ?: targetBuyPrice) * filledQuantity
        val soldProceeds = (averageSoldPrice ?: 0.0) * soldQuantity
        return FinalPriceBatingV1StrategyExecutionView(
            executionId = executionId,
            symbol = symbol,
            market = market,
            status = status,
            budget = budget,
            targetBuyPrice = targetBuyPrice,
            quantity = quantity,
            filledQuantity = filledQuantity,
            averageFilledPrice = averageFilledPrice,
            remainingBuyQuantity = (quantity - filledQuantity).coerceAtLeast(0),
            sellTargetPrice = sellTargetPrice,
            sellQuantity = sellQuantity,
            soldQuantity = soldQuantity,
            averageSoldPrice = averageSoldPrice,
            remainingSellQuantity = (sellQuantity - soldQuantity).coerceAtLeast(0),
            currentCash = budget - filledCost + soldProceeds,
            currentHoldingQuantity = currentHoldingQuantity,
            currentAveragePrice = averageFilledPrice.takeIf { currentHoldingQuantity > 0 },
            sellIntentCreatedAt = sellIntentCreatedAt,
            startedAt = startedAt,
            completedAt = completedAt,
        )
    }
}

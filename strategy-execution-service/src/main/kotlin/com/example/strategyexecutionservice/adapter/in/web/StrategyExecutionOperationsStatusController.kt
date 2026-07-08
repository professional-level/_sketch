package com.example.strategyexecutionservice.adapter.`in`.web

import com.example.common.WebAdapter
import com.example.strategyexecutionservice.application.port.`in`.FinalPriceBatingV1StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.GetStrategyExecutionOperationsStatusUseCase
import com.example.strategyexecutionservice.application.port.`in`.LaorV4StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.StrategyExecutionOperationsStatusResult
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatusCount
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.time.ZonedDateTime

@WebAdapter
@RequestMapping("/operations/strategy-execution")
internal class StrategyExecutionOperationsStatusController(
    private val getStrategyExecutionOperationsStatusUseCase: GetStrategyExecutionOperationsStatusUseCase,
) {

    @GetMapping("/status")
    suspend fun status(): StrategyExecutionOperationsStatusResponse {
        return getStrategyExecutionOperationsStatusUseCase.execute().toResponse()
    }
}

internal data class StrategyExecutionOperationsStatusResponse(
    val generatedAt: ZonedDateTime,
    val orderIntentOutboxStatusCounts: List<StrategyExecutionStatusCount>,
    val strategyExecutionStartRequestCount: Long,
    val strategyExecutionOrderEventTypeCounts: List<StrategyExecutionStatusCount>,
    val laorV4StatusCounts: List<StrategyExecutionStatusCount>,
    val finalPriceBatingV1StatusCounts: List<StrategyExecutionStatusCount>,
    val laorOrderAnomalyTypeCounts: List<StrategyExecutionStatusCount>,
    val activeLaorV4Strategies: List<LaorV4StrategyExecutionResponse>,
    val activeFinalPriceBatingV1Strategies: List<FinalPriceBatingV1StrategyExecutionResponse>,
)

private fun StrategyExecutionOperationsStatusResult.toResponse(): StrategyExecutionOperationsStatusResponse {
    return StrategyExecutionOperationsStatusResponse(
        generatedAt = generatedAt,
        orderIntentOutboxStatusCounts = snapshot.orderIntentOutboxStatusCounts,
        strategyExecutionStartRequestCount = snapshot.strategyExecutionStartRequestCount,
        strategyExecutionOrderEventTypeCounts = snapshot.strategyExecutionOrderEventTypeCounts,
        laorV4StatusCounts = snapshot.laorV4StatusCounts,
        finalPriceBatingV1StatusCounts = snapshot.finalPriceBatingV1StatusCounts,
        laorOrderAnomalyTypeCounts = snapshot.laorOrderAnomalyTypeCounts,
        activeLaorV4Strategies = activeLaorV4Strategies.map { it.toResponse() },
        activeFinalPriceBatingV1Strategies = activeFinalPriceBatingV1Strategies.map { it.toResponse() },
    )
}

private fun LaorV4StrategyExecutionView.toResponse(): LaorV4StrategyExecutionResponse {
    return LaorV4StrategyExecutionResponse(
        executionId = executionId,
        symbol = symbol.ticker,
        status = status.name,
        cycleNo = cycleNo,
        totalSplitCount = totalSplitCount,
        firstBuyLimitPercentAbovePreviousClose = firstBuyLimitPercentAbovePreviousClose,
        autoRestart = autoRestart,
        mode = mode.name,
        progressRound = progressRound,
        availableCash = availableCash,
        holdingQuantity = holdingQuantity,
        averagePurchasePrice = averagePurchasePrice,
        realizedProfitLoss = realizedProfitLoss,
        reverseModeElapsedDays = reverseModeElapsedDays,
        lastExecutionRunId = lastExecutionRunId,
        lastExecutedAt = lastExecutedAt,
    )
}

private fun FinalPriceBatingV1StrategyExecutionView.toResponse(): FinalPriceBatingV1StrategyExecutionResponse {
    return FinalPriceBatingV1StrategyExecutionResponse(
        executionId = executionId,
        symbol = symbol,
        market = market,
        status = status.name,
        budget = budget,
        targetBuyPrice = targetBuyPrice,
        quantity = quantity,
        filledQuantity = filledQuantity,
        averageFilledPrice = averageFilledPrice,
        remainingBuyQuantity = remainingBuyQuantity,
        sellTargetPrice = sellTargetPrice,
        sellQuantity = sellQuantity,
        soldQuantity = soldQuantity,
        averageSoldPrice = averageSoldPrice,
        remainingSellQuantity = remainingSellQuantity,
        currentCash = currentCash,
        currentHoldingQuantity = currentHoldingQuantity,
        currentAveragePrice = currentAveragePrice,
        sellIntentCreatedAt = sellIntentCreatedAt,
        startedAt = startedAt,
        completedAt = completedAt,
    )
}

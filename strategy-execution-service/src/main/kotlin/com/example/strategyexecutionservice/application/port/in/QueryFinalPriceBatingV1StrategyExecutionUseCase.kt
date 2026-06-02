package com.example.strategyexecutionservice.application.port.`in`

import com.example.common.UseCase
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import java.time.ZonedDateTime

@UseCase
interface QueryFinalPriceBatingV1StrategyExecutionUseCase {
    suspend fun find(executionId: String): FinalPriceBatingV1StrategyExecutionView?
    suspend fun findActive(): List<FinalPriceBatingV1StrategyExecutionView>
}

data class FinalPriceBatingV1StrategyExecutionView(
    val executionId: String,
    val symbol: String,
    val market: String,
    val status: StrategyExecutionLifecycleStatus,
    val budget: Double,
    val targetBuyPrice: Double,
    val quantity: Long,
    val filledQuantity: Long,
    val averageFilledPrice: Double?,
    val remainingBuyQuantity: Long,
    val sellTargetPrice: Double?,
    val sellQuantity: Long,
    val soldQuantity: Long,
    val averageSoldPrice: Double?,
    val remainingSellQuantity: Long,
    val currentCash: Double,
    val currentHoldingQuantity: Long,
    val currentAveragePrice: Double?,
    val sellIntentCreatedAt: ZonedDateTime?,
    val startedAt: ZonedDateTime,
    val completedAt: ZonedDateTime?,
)

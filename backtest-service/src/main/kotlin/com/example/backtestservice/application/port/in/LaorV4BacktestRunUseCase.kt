package com.example.backtestservice.application.port.`in`

import com.example.backtestservice.domain.backtest.BacktestRunStatus
import com.example.common.UseCase
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@UseCase
interface RunLaorV4BacktestUseCase {
    fun execute(command: RunLaorV4BacktestCommand): LaorV4BacktestRunResponse
}

@UseCase
interface FindLaorV4BacktestRunUseCase {
    fun find(runId: UUID): LaorV4BacktestRunResponse
}

data class RunLaorV4BacktestCommand(
    val symbol: String,
    val market: String = "US",
    val from: LocalDate,
    val to: LocalDate,
    val initialCash: BigDecimal = BigDecimal("10000"),
    val totalSplitCount: Int,
    val firstBuyLimitPercentAbovePreviousClose: Double,
    val autoRestart: Boolean = true,
    val dividendReinvestment: Boolean = false,
    val autoAdjust: Boolean = false,
    val marketDataTimeoutSeconds: Long = 30,
)

data class LaorV4BacktestRunResponse(
    val runId: UUID,
    val status: BacktestRunStatus,
    val symbol: String,
    val market: String,
    val from: LocalDate,
    val to: LocalDate,
    val initialCash: BigDecimal,
    val finalEquity: BigDecimal,
    val finalProfit: BigDecimal,
    val finalReturnPercent: BigDecimal,
    val realizedProfitLoss: BigDecimal,
    val realizedProfitLossPercent: BigDecimal,
    val dividendIncome: BigDecimal,
    val dividendIncomePercent: BigDecimal,
    val finalCash: BigDecimal,
    val finalHoldingQuantity: Long,
    val finalHoldingAveragePrice: BigDecimal,
    val finalHoldingClose: BigDecimal,
    val finalHoldingMarketValue: BigDecimal,
    val finalHoldingMarketValuePercent: BigDecimal,
    val finalHoldingUnrealizedProfitLoss: BigDecimal,
    val finalHoldingUnrealizedReturnPercent: BigDecimal,
    val tradeCount: Int,
    val equityPointCount: Int,
    val cycleCount: Int,
    val cycles: List<LaorV4BacktestCycleResult>,
)

data class LaorV4BacktestCycleResult(
    val cycleNo: Int,
    val from: LocalDate,
    val to: LocalDate,
    val tradeCount: Int,
    val buyCount: Int,
    val sellCount: Int,
    val realizedProfitLoss: BigDecimal,
    val realizedProfitLossPercent: BigDecimal,
    val dividendIncome: BigDecimal,
    val dividendIncomePercent: BigDecimal,
    val endingEquity: BigDecimal,
    val endingCash: BigDecimal,
    val endingHoldingQuantity: Long,
    val endingHoldingMarketValue: BigDecimal,
    val endingHoldingMarketValuePercent: BigDecimal,
    val closed: Boolean,
)

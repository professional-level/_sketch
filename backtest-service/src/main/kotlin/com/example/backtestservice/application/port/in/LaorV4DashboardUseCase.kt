package com.example.backtestservice.application.port.`in`

import com.example.common.UseCase
import java.math.BigDecimal
import java.time.LocalDate

@UseCase
interface CalculateLaorV4DashboardUseCase {
    fun calculate(command: CalculateLaorV4DashboardCommand): LaorV4DashboardResponse
}

data class CalculateLaorV4DashboardCommand(
    val symbol: String,
    val market: String = "US",
    val startDate: LocalDate,
    val asOfDate: LocalDate? = null,
    val initialCash: BigDecimal,
    val totalSplitCount: Int,
    val firstBuyLimitPercentAbovePreviousClose: Double,
    val autoRestart: Boolean = true,
    val dividendReinvestment: Boolean = false,
    val autoAdjust: Boolean = false,
    val commissionRate: BigDecimal = BigDecimal("0.0005"),
    val slippageRate: BigDecimal = BigDecimal.ZERO,
    val marketDataTimeoutSeconds: Long = 30,
)

data class LaorV4DashboardResponse(
    val symbol: String,
    val market: String,
    val startDate: LocalDate,
    val requestedAsOfDate: LocalDate?,
    val resolvedAsOfDate: LocalDate,
    val parameters: LaorV4DashboardParameters,
    val current: LaorV4DashboardCurrentState,
    val valuation: LaorV4DashboardValuation,
    val nextOrders: List<LaorV4DashboardNextOrder>,
    val nextOrderContext: LaorV4DashboardNextOrderContext,
    val cycleSummary: LaorV4DashboardCycleSummary,
    val dataCoverage: LaorV4DashboardDataCoverage,
)

data class LaorV4DashboardParameters(
    val totalSplitCount: Int,
    val firstBuyLimitPercentAbovePreviousClose: Double,
    val autoRestart: Boolean,
    val dividendReinvestment: Boolean,
    val autoAdjust: Boolean,
    val commissionRate: BigDecimal,
    val slippageRate: BigDecimal,
)

data class LaorV4DashboardCurrentState(
    val cycleNo: Int,
    val mode: String,
    val progressRound: BigDecimal,
    val cash: BigDecimal,
    val holdingQuantity: Long,
    val averagePurchasePrice: BigDecimal,
    val realizedProfitLoss: BigDecimal,
    val dividendIncome: BigDecimal,
)

data class LaorV4DashboardValuation(
    val close: BigDecimal,
    val positionMarketValue: BigDecimal,
    val grossEquity: BigDecimal,
    val netEquity: BigDecimal,
    val totalProfitLoss: BigDecimal,
    val totalReturnPercent: BigDecimal,
    val positionUnrealizedProfitLoss: BigDecimal,
    val positionReturnPercent: BigDecimal,
)

data class LaorV4DashboardNextOrder(
    val side: String,
    val orderType: String,
    val price: BigDecimal?,
    val quantity: Long,
    val orderTag: String,
    val notional: BigDecimal?,
)

data class LaorV4DashboardNextOrderContext(
    val orderSessionDate: LocalDate,
    val previousClose: BigDecimal,
    val starPercent: BigDecimal?,
    val starPrice: BigDecimal?,
    val starBuyPrice: BigDecimal?,
    val targetSellPrice: BigDecimal?,
    val oneBuyBudget: BigDecimal,
    val reverseStarPrice: BigDecimal?,
)

data class LaorV4DashboardCycleSummary(
    val cycleCount: Int,
    val completedCycleCount: Int,
    val currentCycleStartedAt: LocalDate?,
)

data class LaorV4DashboardDataCoverage(
    val from: LocalDate,
    val to: LocalDate,
    val candleCount: Int,
)

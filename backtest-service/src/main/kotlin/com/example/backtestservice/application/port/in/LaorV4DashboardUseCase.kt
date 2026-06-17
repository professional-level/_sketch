package com.example.backtestservice.application.port.`in`

import com.example.backtestservice.domain.backtest.BacktestTrade
import com.example.common.UseCase
import java.math.BigDecimal
import java.time.LocalDate

@UseCase
interface CalculateLaorV4DashboardUseCase {
    fun calculate(command: CalculateLaorV4DashboardCommand): LaorV4DashboardResponse
}

@UseCase
interface FindLaorV4DashboardDetailsUseCase {
    fun findTrades(query: FindLaorV4DashboardTradesQuery): LaorV4DashboardTradesPage
    fun findDailyFlow(query: FindLaorV4DashboardDailyFlowQuery): LaorV4DashboardDailyFlowPage
    fun findIndex(query: FindLaorV4DashboardIndexQuery): LaorV4DashboardIndexResponse
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

data class FindLaorV4DashboardTradesQuery(
    val command: CalculateLaorV4DashboardCommand,
    val page: Int = 0,
    val size: Int = 100,
    val sort: LaorV4DashboardDetailSort = LaorV4DashboardDetailSort.ASC,
)

data class FindLaorV4DashboardDailyFlowQuery(
    val command: CalculateLaorV4DashboardCommand,
    val page: Int = 0,
    val size: Int = 100,
    val sort: LaorV4DashboardDetailSort = LaorV4DashboardDetailSort.ASC,
)

data class FindLaorV4DashboardIndexQuery(
    val command: CalculateLaorV4DashboardCommand,
)

enum class LaorV4DashboardDetailSort {
    ASC,
    DESC,
}

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

data class LaorV4DashboardTradesPage(
    val symbol: String,
    val market: String,
    val startDate: LocalDate,
    val requestedAsOfDate: LocalDate?,
    val resolvedAsOfDate: LocalDate,
    val page: Int,
    val size: Int,
    val total: Int,
    val items: List<BacktestTrade>,
)

data class LaorV4DashboardDailyFlowPage(
    val symbol: String,
    val market: String,
    val startDate: LocalDate,
    val requestedAsOfDate: LocalDate?,
    val resolvedAsOfDate: LocalDate,
    val page: Int,
    val size: Int,
    val total: Int,
    val items: List<LaorV4DashboardDailyFlowItem>,
)

data class LaorV4DashboardDailyFlowItem(
    val date: LocalDate,
    val referenceDate: LocalDate,
    val cycleNo: Int,
    val previousClose: BigDecimal,
    val close: BigDecimal,
    val orders: List<LaorV4DashboardNextOrder>,
    val filledOrders: List<BacktestTrade>,
    val before: LaorV4DashboardDailyState,
    val after: LaorV4DashboardDailyState,
    val dailyDividendIncome: BigDecimal,
    val cycleClosed: Boolean,
    val tradingCompleted: Boolean,
)

data class LaorV4DashboardDailyState(
    val cycleNo: Int,
    val mode: String,
    val progressRound: BigDecimal,
    val cash: BigDecimal,
    val holdingQuantity: Long,
    val averagePurchasePrice: BigDecimal,
    val realizedProfitLoss: BigDecimal,
    val dividendIncome: BigDecimal,
)

data class LaorV4DashboardIndexResponse(
    val symbol: String,
    val market: String,
    val startDate: LocalDate,
    val requestedAsOfDate: LocalDate?,
    val resolvedAsOfDate: LocalDate,
    val base: LaorV4DashboardIndexBase,
    val series: List<LaorV4DashboardChartSeries>,
    val points: List<LaorV4DashboardIndexPoint>,
)

data class LaorV4DashboardIndexBase(
    val baseDate: LocalDate,
    val baseValue: BigDecimal,
    val initialCash: BigDecimal,
    val benchmarkSymbol: String,
    val benchmarkClose: BigDecimal,
)

data class LaorV4DashboardChartSeries(
    val key: String,
    val label: String,
    val chartType: String,
    val color: String,
    val data: List<LaorV4DashboardChartPoint>,
)

data class LaorV4DashboardChartPoint(
    val time: LocalDate,
    val value: BigDecimal,
)

data class LaorV4DashboardIndexPoint(
    val date: LocalDate,
    val laorIndex: BigDecimal,
    val benchmarkIndex: BigDecimal,
    val netEquity: BigDecimal,
    val cash: BigDecimal,
    val holdingQuantity: Long,
    val averagePurchasePrice: BigDecimal,
    val realizedProfitLoss: BigDecimal,
    val dividendIncome: BigDecimal,
    val progressRound: BigDecimal,
    val cycleNo: Int,
    val mode: String,
    val close: BigDecimal,
    val benchmarkClose: BigDecimal,
    val basePoint: Boolean,
)

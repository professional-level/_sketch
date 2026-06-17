package com.example.backtestservice.application.port.`in`

import com.example.backtestservice.domain.backtest.BacktestTrade
import com.example.common.UseCase
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@UseCase
interface CreateLaorV4PortfolioUseCase {
    fun create(command: CreateLaorV4PortfolioCommand): LaorV4PortfolioResponse
}

@UseCase
interface FindLaorV4PortfolioUseCase {
    fun find(portfolioId: UUID): LaorV4PortfolioResponse
    fun findAll(): List<LaorV4PortfolioResponse>
}

@UseCase
interface CalculateLaorV4PortfolioDashboardUseCase {
    fun calculate(query: CalculateLaorV4PortfolioDashboardQuery): LaorV4PortfolioDashboardResponse
}

@UseCase
interface FindLaorV4PortfolioDetailsUseCase {
    fun findTrades(query: FindLaorV4PortfolioTradesQuery): LaorV4PortfolioTradesPage
    fun findDailyFlow(query: FindLaorV4PortfolioDailyFlowQuery): LaorV4PortfolioDailyFlowPage
    fun findIndex(query: FindLaorV4PortfolioIndexQuery): LaorV4PortfolioIndexResponse
}

data class CreateLaorV4PortfolioCommand(
    val name: String? = null,
    val symbol: String,
    val market: String = "US",
    val startDate: LocalDate,
    val initialCash: BigDecimal,
    val totalSplitCount: Int,
    val firstBuyLimitPercentAbovePreviousClose: Double,
    val autoRestart: Boolean = true,
    val dividendReinvestment: Boolean = false,
    val autoAdjust: Boolean = false,
    val commissionRate: BigDecimal = BigDecimal("0.0005"),
    val slippageRate: BigDecimal = BigDecimal.ZERO,
)

data class CalculateLaorV4PortfolioDashboardQuery(
    val portfolioId: UUID,
    val asOfDate: LocalDate? = null,
    val marketDataTimeoutSeconds: Long = 30,
)

data class FindLaorV4PortfolioTradesQuery(
    val portfolioId: UUID,
    val asOfDate: LocalDate? = null,
    val page: Int = 0,
    val size: Int = 100,
    val sort: LaorV4DashboardDetailSort = LaorV4DashboardDetailSort.ASC,
    val marketDataTimeoutSeconds: Long = 30,
)

data class FindLaorV4PortfolioDailyFlowQuery(
    val portfolioId: UUID,
    val asOfDate: LocalDate? = null,
    val page: Int = 0,
    val size: Int = 100,
    val sort: LaorV4DashboardDetailSort = LaorV4DashboardDetailSort.ASC,
    val marketDataTimeoutSeconds: Long = 30,
)

data class FindLaorV4PortfolioIndexQuery(
    val portfolioId: UUID,
    val asOfDate: LocalDate? = null,
    val marketDataTimeoutSeconds: Long = 30,
)

data class LaorV4PortfolioDashboardResponse(
    val portfolio: LaorV4PortfolioResponse,
    val dashboard: LaorV4DashboardResponse,
)

data class LaorV4PortfolioTradesPage(
    val portfolioId: UUID,
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

data class LaorV4PortfolioDailyFlowPage(
    val portfolioId: UUID,
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

data class LaorV4PortfolioIndexResponse(
    val portfolioId: UUID,
    val symbol: String,
    val market: String,
    val startDate: LocalDate,
    val requestedAsOfDate: LocalDate?,
    val resolvedAsOfDate: LocalDate,
    val base: LaorV4DashboardIndexBase,
    val series: List<LaorV4DashboardChartSeries>,
    val points: List<LaorV4DashboardIndexPoint>,
)

data class LaorV4PortfolioResponse(
    val portfolioId: UUID,
    val name: String?,
    val symbol: String,
    val market: String,
    val startDate: LocalDate,
    val initialCash: BigDecimal,
    val totalSplitCount: Int,
    val firstBuyLimitPercentAbovePreviousClose: Double,
    val autoRestart: Boolean,
    val dividendReinvestment: Boolean,
    val autoAdjust: Boolean,
    val commissionRate: BigDecimal,
    val slippageRate: BigDecimal,
    val latestSnapshot: LaorV4PortfolioSnapshotResponse?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class LaorV4PortfolioSnapshotResponse(
    val resolvedAsOfDate: LocalDate,
    val calculatedAt: Instant,
    val cycleNo: Int,
    val mode: String,
    val progressRound: BigDecimal,
    val cash: BigDecimal,
    val holdingQuantity: Long,
    val averagePurchasePrice: BigDecimal,
    val realizedProfitLoss: BigDecimal,
    val dividendIncome: BigDecimal,
    val netEquity: BigDecimal,
    val totalProfitLoss: BigDecimal,
    val totalReturnPercent: BigDecimal,
)

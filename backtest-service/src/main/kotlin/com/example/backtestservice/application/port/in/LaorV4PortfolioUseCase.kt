package com.example.backtestservice.application.port.`in`

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

data class LaorV4PortfolioDashboardResponse(
    val portfolio: LaorV4PortfolioResponse,
    val dashboard: LaorV4DashboardResponse,
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

package com.example.backtestservice.domain.backtest

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LaorV4PortfolioRecord(
    val portfolioId: UUID,
    val name: String? = null,
    val symbol: String,
    val market: String,
    val startDate: LocalDate,
    val initialCash: BigDecimal,
    val totalSplitCount: Int,
    val firstBuyLimitPercentAbovePreviousClose: Double,
    val autoRestart: Boolean = true,
    val dividendReinvestment: Boolean = false,
    val autoAdjust: Boolean = false,
    val commissionRate: BigDecimal = BigDecimal("0.0005"),
    val slippageRate: BigDecimal = BigDecimal.ZERO,
    val latestSnapshot: LaorV4PortfolioSnapshot? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class LaorV4PortfolioSnapshot(
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

package com.example.backtestservice.domain.backtest

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class BacktestStrategyType {
    BUY_AND_HOLD,
}

enum class BacktestTradeSide {
    BUY,
    SELL,
}

data class BacktestTrade(
    val side: BacktestTradeSide,
    val symbol: String,
    val date: LocalDate,
    val quantity: Long,
    val price: BigDecimal,
    val notional: BigDecimal,
    val commission: BigDecimal,
)

data class BacktestEquityPoint(
    val date: LocalDate,
    val equity: BigDecimal,
    val cash: BigDecimal,
    val positionQuantity: Long,
    val close: BigDecimal,
)

data class BacktestResult(
    val runId: UUID,
    val symbol: String,
    val market: String,
    val strategyType: BacktestStrategyType,
    val from: LocalDate,
    val to: LocalDate,
    val initialCash: BigDecimal,
    val finalEquity: BigDecimal,
    val totalReturn: BigDecimal,
    val maxDrawdown: BigDecimal,
    val trades: List<BacktestTrade>,
    val equityCurve: List<BacktestEquityPoint>,
)

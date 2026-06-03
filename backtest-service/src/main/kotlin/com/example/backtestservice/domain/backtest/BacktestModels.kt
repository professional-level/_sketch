package com.example.backtestservice.domain.backtest

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class BacktestStrategyType {
    BUY_AND_HOLD,
    LAOR_V4,
}

enum class BacktestTradeSide {
    BUY,
    SELL,
}

enum class BacktestRunStatus {
    COMPLETED,
}

data class BacktestTrade(
    val side: BacktestTradeSide,
    val symbol: String,
    val date: LocalDate,
    val quantity: Long,
    val price: BigDecimal,
    val notional: BigDecimal,
    val commission: BigDecimal,
    val orderType: String? = null,
    val orderTag: String? = null,
    val cycleNo: Int? = null,
)

data class BacktestEquityPoint(
    val date: LocalDate,
    val equity: BigDecimal,
    val cash: BigDecimal,
    val positionQuantity: Long,
    val close: BigDecimal,
    val averagePurchasePrice: BigDecimal? = null,
    val realizedProfitLoss: BigDecimal? = null,
    val cycleNo: Int? = null,
    val strategyMode: String? = null,
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
) {
    fun toRunRecord(): BacktestRunRecord {
        return BacktestRunRecord(
            summary = BacktestRunSummary(
                runId = runId,
                status = BacktestRunStatus.COMPLETED,
                symbol = symbol,
                market = market,
                strategyType = strategyType,
                from = from,
                to = to,
                initialCash = initialCash,
                finalEquity = finalEquity,
                totalReturn = totalReturn,
                maxDrawdown = maxDrawdown,
                tradeCount = trades.size,
                equityPointCount = equityCurve.size,
            ),
            trades = trades,
            equityCurve = equityCurve,
        )
    }
}

data class BacktestRunSummary(
    val runId: UUID,
    val status: BacktestRunStatus,
    val symbol: String,
    val market: String,
    val strategyType: BacktestStrategyType,
    val from: LocalDate,
    val to: LocalDate,
    val initialCash: BigDecimal,
    val finalEquity: BigDecimal,
    val totalReturn: BigDecimal,
    val maxDrawdown: BigDecimal,
    val tradeCount: Int,
    val equityPointCount: Int,
)

data class BacktestRunRecord(
    val summary: BacktestRunSummary,
    val trades: List<BacktestTrade>,
    val equityCurve: List<BacktestEquityPoint>,
)

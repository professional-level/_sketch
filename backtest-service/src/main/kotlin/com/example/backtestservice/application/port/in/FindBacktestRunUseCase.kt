package com.example.backtestservice.application.port.`in`

import com.example.backtestservice.domain.backtest.BacktestEquityPoint
import com.example.backtestservice.domain.backtest.BacktestRunSummary
import com.example.backtestservice.domain.backtest.BacktestTrade
import com.example.common.UseCase
import java.time.LocalDate
import java.util.UUID

@UseCase
interface FindBacktestRunUseCase {
    fun findSummary(runId: UUID): BacktestRunSummary
    fun findTrades(query: FindBacktestTradesQuery): BacktestTradesPage
    fun findEquityCurve(query: FindBacktestEquityCurveQuery): BacktestEquityCurveResult
}

data class FindBacktestTradesQuery(
    val runId: UUID,
    val page: Int = 0,
    val size: Int = 100,
)

data class BacktestTradesPage(
    val runId: UUID,
    val page: Int,
    val size: Int,
    val total: Int,
    val items: List<BacktestTrade>,
)

data class FindBacktestEquityCurveQuery(
    val runId: UUID,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val granularity: BacktestEquityCurveGranularity = BacktestEquityCurveGranularity.DAILY,
)

enum class BacktestEquityCurveGranularity {
    DAILY,
    MONTHLY,
}

data class BacktestEquityCurveResult(
    val runId: UUID,
    val granularity: BacktestEquityCurveGranularity,
    val total: Int,
    val points: List<BacktestEquityPoint>,
)

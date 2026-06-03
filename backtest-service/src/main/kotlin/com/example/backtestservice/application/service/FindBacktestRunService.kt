package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.BacktestEquityCurveGranularity
import com.example.backtestservice.application.port.`in`.BacktestEquityCurveResult
import com.example.backtestservice.application.port.`in`.BacktestTradesPage
import com.example.backtestservice.application.port.`in`.FindBacktestEquityCurveQuery
import com.example.backtestservice.application.port.`in`.FindBacktestRunUseCase
import com.example.backtestservice.application.port.`in`.FindBacktestTradesQuery
import com.example.backtestservice.application.port.out.BacktestRunStorePort
import com.example.backtestservice.domain.backtest.BacktestEquityPoint
import com.example.backtestservice.domain.backtest.BacktestRunRecord
import com.example.backtestservice.domain.backtest.BacktestRunSummary
import com.example.common.UseCaseImpl
import java.time.YearMonth
import java.util.UUID

@UseCaseImpl
class FindBacktestRunService(
    private val backtestRunStorePort: BacktestRunStorePort,
) : FindBacktestRunUseCase {
    override fun findSummary(runId: UUID): BacktestRunSummary {
        return findRecord(runId).summary
    }

    override fun findTrades(query: FindBacktestTradesQuery): BacktestTradesPage {
        query.validate()
        val trades = findRecord(query.runId).trades
        val offset = query.page * query.size
        return BacktestTradesPage(
            runId = query.runId,
            page = query.page,
            size = query.size,
            total = trades.size,
            items = trades.drop(offset).take(query.size),
        )
    }

    override fun findEquityCurve(query: FindBacktestEquityCurveQuery): BacktestEquityCurveResult {
        query.validate()
        val points = findRecord(query.runId).equityCurve
            .asSequence()
            .filter { query.from == null || it.date >= query.from }
            .filter { query.to == null || it.date <= query.to }
            .toList()
            .withGranularity(query.granularity)
        return BacktestEquityCurveResult(
            runId = query.runId,
            granularity = query.granularity,
            total = points.size,
            points = points,
        )
    }

    private fun findRecord(runId: UUID): BacktestRunRecord {
        return backtestRunStorePort.findById(runId)
            ?: throw NoSuchElementException("backtest run not found: $runId")
    }

    private fun FindBacktestTradesQuery.validate() {
        require(page >= 0) { "page must be zero or positive" }
        require(size in 1..1000) { "size must be between 1 and 1000" }
    }

    private fun FindBacktestEquityCurveQuery.validate() {
        require(from == null || to == null || !from.isAfter(to)) { "from must be on or before to" }
    }

    private fun List<BacktestEquityPoint>.withGranularity(
        granularity: BacktestEquityCurveGranularity,
    ): List<BacktestEquityPoint> {
        return when (granularity) {
            BacktestEquityCurveGranularity.DAILY -> this
            BacktestEquityCurveGranularity.MONTHLY -> groupBy { YearMonth.from(it.date) }
                .values
                .mapNotNull { points -> points.maxByOrNull { it.date } }
                .sortedBy { it.date }
        }
    }
}

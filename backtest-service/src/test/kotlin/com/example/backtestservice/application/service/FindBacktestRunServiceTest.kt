package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.BacktestEquityCurveGranularity
import com.example.backtestservice.application.port.`in`.FindBacktestEquityCurveQuery
import com.example.backtestservice.application.port.`in`.FindBacktestTradesQuery
import com.example.backtestservice.application.port.out.BacktestRunStorePort
import com.example.backtestservice.domain.backtest.BacktestEquityPoint
import com.example.backtestservice.domain.backtest.BacktestResult
import com.example.backtestservice.domain.backtest.BacktestRunRecord
import com.example.backtestservice.domain.backtest.BacktestStrategyType
import com.example.backtestservice.domain.backtest.BacktestTrade
import com.example.backtestservice.domain.backtest.BacktestTradeSide
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FindBacktestRunServiceTest {
    private val runId: UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    @Test
    fun `returns summary and paged trades`() {
        val service = FindBacktestRunService(FakeBacktestRunStorePort(record()))

        val summary = service.findSummary(runId)
        val trades = service.findTrades(FindBacktestTradesQuery(runId = runId, page = 0, size = 1))

        assertEquals("TQQQ", summary.symbol)
        assertEquals(2, trades.total)
        assertEquals(1, trades.items.size)
        assertEquals(LocalDate.parse("2024-01-02"), trades.items.single().date)
    }

    @Test
    fun `returns monthly equity curve using last point in each month`() {
        val service = FindBacktestRunService(FakeBacktestRunStorePort(record()))

        val curve = service.findEquityCurve(
            FindBacktestEquityCurveQuery(
                runId = runId,
                granularity = BacktestEquityCurveGranularity.MONTHLY,
            ),
        )

        assertEquals(BacktestEquityCurveGranularity.MONTHLY, curve.granularity)
        assertEquals(2, curve.points.size)
        assertEquals(LocalDate.parse("2024-01-31"), curve.points[0].date)
        assertEquals(LocalDate.parse("2024-02-01"), curve.points[1].date)
    }

    @Test
    fun `fails when run does not exist`() {
        val service = FindBacktestRunService(FakeBacktestRunStorePort(null))

        assertFailsWith<NoSuchElementException> {
            service.findSummary(runId)
        }
    }

    private fun record(): BacktestRunRecord {
        return BacktestResult(
            runId = runId,
            symbol = "TQQQ",
            market = "US",
            strategyType = BacktestStrategyType.BUY_AND_HOLD,
            from = LocalDate.parse("2024-01-02"),
            to = LocalDate.parse("2024-02-01"),
            initialCash = "1000".toBigDecimal(),
            finalEquity = "1100".toBigDecimal(),
            totalReturn = "0.1".toBigDecimal(),
            maxDrawdown = "0.05".toBigDecimal(),
            trades = listOf(
                trade("2024-01-02"),
                trade("2024-01-31"),
            ),
            equityCurve = listOf(
                point("2024-01-02", "1000"),
                point("2024-01-31", "1050"),
                point("2024-02-01", "1100"),
            ),
        ).toRunRecord()
    }

    private fun trade(date: String): BacktestTrade {
        return BacktestTrade(
            side = BacktestTradeSide.BUY,
            symbol = "TQQQ",
            date = LocalDate.parse(date),
            quantity = 10,
            price = "10".toBigDecimal(),
            notional = "100".toBigDecimal(),
            commission = "0".toBigDecimal(),
        )
    }

    private fun point(date: String, equity: String): BacktestEquityPoint {
        return BacktestEquityPoint(
            date = LocalDate.parse(date),
            equity = equity.toBigDecimal(),
            cash = "0".toBigDecimal(),
            positionQuantity = 10,
            close = "10".toBigDecimal(),
        )
    }

    private class FakeBacktestRunStorePort(
        private val record: BacktestRunRecord?,
    ) : BacktestRunStorePort {
        override fun save(record: BacktestRunRecord) {
        }

        override fun findById(runId: UUID): BacktestRunRecord? {
            return record?.takeIf { it.summary.runId == runId }
        }
    }
}

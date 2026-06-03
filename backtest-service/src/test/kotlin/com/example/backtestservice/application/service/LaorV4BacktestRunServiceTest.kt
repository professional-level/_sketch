package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.ExecuteBacktestRunCommand
import com.example.backtestservice.application.port.`in`.ExecuteBacktestRunUseCase
import com.example.backtestservice.application.port.`in`.RunLaorV4BacktestCommand
import com.example.backtestservice.application.port.out.BacktestRunStorePort
import com.example.backtestservice.domain.backtest.BacktestEquityPoint
import com.example.backtestservice.domain.backtest.BacktestResult
import com.example.backtestservice.domain.backtest.BacktestRunRecord
import com.example.backtestservice.domain.backtest.BacktestRunSummary
import com.example.backtestservice.domain.backtest.BacktestStrategyType
import com.example.backtestservice.domain.backtest.BacktestTrade
import com.example.backtestservice.domain.backtest.BacktestTradeSide
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class LaorV4BacktestRunServiceTest {
    private val runId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")

    @Test
    fun `returns Laor V4 run response with final and cycle metrics`() {
        val store = FakeBacktestRunStorePort(record())
        val service = LaorV4BacktestRunService(
            executeBacktestRunUseCase = FakeExecuteBacktestRunUseCase(store),
            backtestRunStorePort = store,
        )

        val response = service.execute(
            RunLaorV4BacktestCommand(
                symbol = "TQQQ",
                from = LocalDate.parse("2024-01-02"),
                to = LocalDate.parse("2024-01-04"),
                initialCash = "1000".toBigDecimal(),
            ),
        )

        assertEquals(runId, response.runId)
        assertDecimal("100", response.finalProfit)
        assertDecimal("10.0", response.finalReturnPercent)
        assertDecimal("50", response.realizedProfitLoss)
        assertDecimal("5.00", response.realizedProfitLossPercent)
        assertEquals(10, response.finalHoldingQuantity)
        assertDecimal("300", response.finalHoldingMarketValue)
        assertDecimal("27.27272727272727", response.finalHoldingMarketValuePercent)
        assertDecimal("50", response.finalHoldingUnrealizedProfitLoss)
        assertDecimal("20.0", response.finalHoldingUnrealizedReturnPercent)
        assertEquals(2, response.cycleCount)
        assertDecimal("50", response.cycles[0].realizedProfitLoss)
        assertEquals(true, response.cycles[0].closed)
        assertDecimal("0", response.cycles[1].realizedProfitLoss)
        assertEquals(false, response.cycles[1].closed)
    }

    private fun record(): BacktestRunRecord {
        return BacktestResult(
            runId = runId,
            symbol = "TQQQ",
            market = "US",
            strategyType = BacktestStrategyType.LAOR_V4,
            from = LocalDate.parse("2024-01-02"),
            to = LocalDate.parse("2024-01-04"),
            initialCash = "1000".toBigDecimal(),
            finalEquity = "1100".toBigDecimal(),
            totalReturn = "0.1".toBigDecimal(),
            maxDrawdown = "0".toBigDecimal(),
            trades = listOf(
                trade(BacktestTradeSide.BUY, "2024-01-02", cycleNo = 1),
                trade(BacktestTradeSide.SELL, "2024-01-03", cycleNo = 1),
                trade(BacktestTradeSide.BUY, "2024-01-04", cycleNo = 2),
            ),
            equityCurve = listOf(
                point("2024-01-02", equity = "1000", cash = "800", quantity = 10, close = "20", average = "20", realized = "0", cycleNo = 1),
                point("2024-01-03", equity = "1050", cash = "1050", quantity = 0, close = "25", average = "0", realized = "50", cycleNo = 1),
                point("2024-01-04", equity = "1100", cash = "800", quantity = 10, close = "30", average = "25", realized = "50", cycleNo = 2),
            ),
        ).toRunRecord()
    }

    private fun trade(side: BacktestTradeSide, date: String, cycleNo: Int): BacktestTrade {
        return BacktestTrade(
            side = side,
            symbol = "TQQQ",
            date = LocalDate.parse(date),
            quantity = 10,
            price = "10".toBigDecimal(),
            notional = "100".toBigDecimal(),
            commission = "0".toBigDecimal(),
            orderType = "LOC",
            orderTag = "TEST",
            cycleNo = cycleNo,
        )
    }

    private fun point(
        date: String,
        equity: String,
        cash: String,
        quantity: Long,
        close: String,
        average: String,
        realized: String,
        cycleNo: Int,
    ): BacktestEquityPoint {
        return BacktestEquityPoint(
            date = LocalDate.parse(date),
            equity = equity.toBigDecimal(),
            cash = cash.toBigDecimal(),
            positionQuantity = quantity,
            close = close.toBigDecimal(),
            averagePurchasePrice = average.toBigDecimal(),
            realizedProfitLoss = realized.toBigDecimal(),
            cycleNo = cycleNo,
            strategyMode = "NORMAL",
        )
    }

    private class FakeExecuteBacktestRunUseCase(
        private val store: FakeBacktestRunStorePort,
    ) : ExecuteBacktestRunUseCase {
        override fun execute(command: ExecuteBacktestRunCommand): BacktestRunSummary {
            assertEquals(BacktestStrategyType.LAOR_V4, command.strategyType)
            return checkNotNull(store.record).summary
        }
    }

    private class FakeBacktestRunStorePort(
        val record: BacktestRunRecord?,
    ) : BacktestRunStorePort {
        override fun save(record: BacktestRunRecord) {
        }

        override fun findById(runId: UUID): BacktestRunRecord? {
            return record?.takeIf { it.summary.runId == runId }
        }
    }

    private fun assertDecimal(expected: String, actual: java.math.BigDecimal) {
        assertEquals(0, expected.toBigDecimal().compareTo(actual))
    }
}

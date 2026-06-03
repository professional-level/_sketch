package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.RunBacktestCommand
import com.example.backtestservice.application.port.out.HistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.HistoricalMarketDataPort
import com.example.backtestservice.domain.backtest.BacktestTradeSide
import com.example.backtestservice.domain.market.HistoricalCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RunBacktestServiceTest {
    @Test
    fun `runs buy and hold backtest from daily candles`() {
        val service = RunBacktestService(
            FakeHistoricalMarketDataPort(
                listOf(
                    candle("2024-01-02", open = "10.0", close = "10.0"),
                    candle("2024-01-03", open = "9.0", close = "9.0"),
                    candle("2024-01-04", open = "11.0", close = "12.0"),
                ),
            ),
        )

        val result = service.execute(
            RunBacktestCommand(
                symbol = "tqqq",
                from = LocalDate.parse("2024-01-02"),
                to = LocalDate.parse("2024-01-04"),
                initialCash = "1000".toBigDecimal(),
            ),
        )

        assertEquals("TQQQ", result.symbol)
        assertEquals("US", result.market)
        assertEquals("1200.0".toBigDecimal(), result.finalEquity)
        assertEquals("0.2".toBigDecimal(), result.totalReturn)
        assertEquals("0.1".toBigDecimal(), result.maxDrawdown)
        assertEquals(3, result.equityCurve.size)
        with(result.trades.single()) {
            assertEquals(BacktestTradeSide.BUY, side)
            assertEquals(100, quantity)
            assertEquals("10.0".toBigDecimal(), price)
            assertEquals("1000.0".toBigDecimal(), notional)
        }
    }

    @Test
    fun `fails when no historical candles are available`() {
        val service = RunBacktestService(FakeHistoricalMarketDataPort(emptyList()))

        assertFailsWith<IllegalArgumentException> {
            service.execute(
                RunBacktestCommand(
                    symbol = "TQQQ",
                    from = LocalDate.parse("2024-01-02"),
                    to = LocalDate.parse("2024-01-04"),
                    initialCash = "1000".toBigDecimal(),
                ),
            )
        }
    }

    private fun candle(
        date: String,
        open: String,
        close: String,
    ): HistoricalCandle {
        return HistoricalCandle(
            symbol = "TQQQ",
            market = "US",
            date = LocalDate.parse(date),
            open = open.toBigDecimal(),
            high = close.toBigDecimal(),
            low = close.toBigDecimal(),
            close = close.toBigDecimal(),
            adjustedClose = close.toBigDecimal(),
            volume = 1000,
            source = "TEST",
        )
    }

    private class FakeHistoricalMarketDataPort(
        private val candles: List<HistoricalCandle>,
    ) : HistoricalMarketDataPort {
        override fun findDailyCandles(query: HistoricalDailyCandlesQuery): List<HistoricalCandle> {
            return candles.filter { it.date >= query.from && it.date <= query.to }
        }
    }
}

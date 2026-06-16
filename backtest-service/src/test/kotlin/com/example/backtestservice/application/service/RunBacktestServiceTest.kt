package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.RunBacktestCommand
import com.example.backtestservice.application.port.`in`.LaorV4BacktestParameters
import com.example.backtestservice.application.port.out.HistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.HistoricalMarketDataPort
import com.example.backtestservice.application.port.out.SaveHistoricalDailyCandlesCommand
import com.example.backtestservice.domain.backtest.BacktestStrategyType
import com.example.backtestservice.domain.backtest.BacktestTradeSide
import com.example.backtestservice.domain.market.HistoricalCandle
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyCostPolicy
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyOrder
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyOrderTag
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyOrderType
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySide
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
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

    @Test
    fun `runs Laor V4 backtest through strategy engine fills`() {
        val service = RunBacktestService(
            FakeHistoricalMarketDataPort(
                listOf(
                    candle("2024-01-02", open = "10.0", high = "10.0", low = "10.0", close = "10.0"),
                    candle("2024-01-03", open = "20.0", high = "20.0", low = "20.0", close = "20.0", dividend = "0.5"),
                ),
            ),
        )

        val result = service.execute(
            RunBacktestCommand(
                symbol = "TQQQ",
                from = LocalDate.parse("2024-01-02"),
                to = LocalDate.parse("2024-01-03"),
                initialCash = "1000".toBigDecimal(),
                strategyType = BacktestStrategyType.LAOR_V4,
                laorV4 = LaorV4BacktestParameters(
                    totalSplitCount = 20,
                    firstBuyLimitPercentAbovePreviousClose = 12.0,
                ),
            ),
        )

        assertEquals(BacktestStrategyType.LAOR_V4, result.strategyType)
        assertEquals("1016.5".toBigDecimal(), result.finalEquity)
        assertEquals(3, result.trades.size)
        assertEquals(listOf("FIRST_BUY", "QUARTER_SELL", "TARGET_SELL"), result.trades.map { it.orderTag })
        assertEquals(listOf(BacktestTradeSide.BUY, BacktestTradeSide.SELL, BacktestTradeSide.SELL), result.trades.map { it.side })
        assertEquals(0, result.equityCurve.last().positionQuantity)
        assertEquals("1016.5".toBigDecimal(), result.equityCurve.last().cash)
        assertEquals("14.5".toBigDecimal(), result.equityCurve.last().realizedProfitLoss)
        assertEquals("2.0".toBigDecimal(), result.equityCurve.last().dividendIncome)
    }

    @Test
    fun `Laor V4 slippage does not move LOC fill price beyond limit and is deducted on sell`() {
        val config = LaorV4StrategyConfig(
            symbol = LaorV4StrategySymbol.TQQQ,
            totalSplitCount = 20,
            firstBuyLimitPercentAbovePreviousClose = 12.0,
            costPolicy = LaorV4StrategyCostPolicy(slippageRate = 0.01),
        )
        val candle = candle("2024-01-02", open = "100.0", high = "100.0", low = "100.0", close = "100.0")
        val buyTrade = checkNotNull(
            LaorV4StrategyOrder(
                side = LaorV4StrategySide.BUY,
                type = LaorV4StrategyOrderType.LOC,
                price = 100.0,
                quantity = 1,
                tag = LaorV4StrategyOrderTag.FIRST_BUY,
            ).toFilledOrder(candle),
        ).toBacktestTrade(config, candle.date, cycleNo = 1)
        val sellTrade = checkNotNull(
            LaorV4StrategyOrder(
                side = LaorV4StrategySide.SELL,
                type = LaorV4StrategyOrderType.LOC,
                price = 100.0,
                quantity = 2,
                tag = LaorV4StrategyOrderTag.QUARTER_SELL,
            ).toFilledOrder(candle),
        ).toBacktestTrade(config, candle.date, cycleNo = 1)

        assertEquals("100.0".toBigDecimal(), buyTrade.price)
        assertEquals("0.0".toBigDecimal(), buyTrade.commission)
        assertEquals("100.0".toBigDecimal(), sellTrade.price)
        assertEquals("4.0".toBigDecimal(), sellTrade.commission)
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

    private fun candle(
        date: String,
        open: String,
        high: String,
        low: String,
        close: String,
        dividend: String = "0",
    ): HistoricalCandle {
        return HistoricalCandle(
            symbol = "TQQQ",
            market = "US",
            date = LocalDate.parse(date),
            open = open.toBigDecimal(),
            high = high.toBigDecimal(),
            low = low.toBigDecimal(),
            close = close.toBigDecimal(),
            adjustedClose = close.toBigDecimal(),
            dividend = dividend.toBigDecimal(),
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

        override fun saveDailyCandles(command: SaveHistoricalDailyCandlesCommand) {
        }
    }
}

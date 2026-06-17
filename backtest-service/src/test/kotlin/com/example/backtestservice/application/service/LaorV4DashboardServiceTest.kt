package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.CalculateLaorV4DashboardCommand
import com.example.backtestservice.application.port.`in`.FindLaorV4DashboardDailyFlowQuery
import com.example.backtestservice.application.port.`in`.FindLaorV4DashboardIndexQuery
import com.example.backtestservice.application.port.`in`.FindLaorV4DashboardTradesQuery
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataResult
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataUseCase
import com.example.backtestservice.application.port.`in`.LaorV4DashboardDetailSort
import com.example.backtestservice.application.port.out.HistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.HistoricalMarketDataPort
import com.example.backtestservice.application.port.out.MarketCalendarPort
import com.example.backtestservice.application.port.out.MarketCalendarSource
import com.example.backtestservice.application.port.out.MarketTradingDaysQuery
import com.example.backtestservice.application.port.out.MarketTradingDaysResult
import com.example.backtestservice.application.port.out.SaveHistoricalDailyCandlesCommand
import com.example.backtestservice.domain.market.HistoricalCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LaorV4DashboardServiceTest {
    @Test
    fun `treats start date as first order reference date and replays from next trading day`() {
        val startDate = LocalDate.parse("2024-01-02")
        val asOfDate = LocalDate.parse("2024-01-03")
        val import = FakeImportHistoricalMarketDataUseCase()
        val service = LaorV4DashboardService(
            importHistoricalMarketDataUseCase = import,
            historicalMarketDataPort = FakeHistoricalMarketDataPort(
                listOf(
                    candle("2024-01-02", close = "10.0"),
                    candle("2024-01-03", close = "10.5"),
                ),
            ),
            marketCalendarPort = FakeMarketCalendarPort(
                listOf(
                    LocalDate.parse("2024-01-02"),
                    LocalDate.parse("2024-01-03"),
                    LocalDate.parse("2024-01-04"),
                ),
            ),
        )

        val response = service.calculate(
            CalculateLaorV4DashboardCommand(
                symbol = "tqqq",
                startDate = startDate,
                asOfDate = asOfDate,
                initialCash = "1000".toBigDecimal(),
                totalSplitCount = 30,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
            ),
        )

        assertEquals("TQQQ", response.symbol)
        assertEquals(startDate, response.startDate)
        assertEquals(asOfDate, response.requestedAsOfDate)
        assertEquals(asOfDate, response.resolvedAsOfDate)
        assertEquals(30, response.parameters.totalSplitCount)
        assertDecimal("0.0005", response.parameters.commissionRate)
        assertEquals(1, response.current.cycleNo)
        assertDecimal("1.0", response.current.progressRound)
        assertEquals(2, response.current.holdingQuantity)
        assertDecimal("10.50525", response.current.averagePurchasePrice)
        assertDecimal("978.9895", response.current.cash)
        assertEquals(LocalDate.parse("2024-01-04"), response.nextOrderContext.orderSessionDate)
        assertEquals(listOf("STAR_HALF_BUY", "AVG_HALF_BUY", "TARGET_SELL"), response.nextOrders.map { it.orderTag })
        assertEquals(startDate, response.dataCoverage.from)
        assertEquals(asOfDate, response.dataCoverage.to)
        assertEquals(2, response.dataCoverage.candleCount)
        assertEquals(startDate.minusDays(14), import.commands.single().from)
        assertEquals(asOfDate, import.commands.single().to)
    }

    @Test
    fun `returns initial state and first order when as-of date is start reference date`() {
        val startDate = LocalDate.parse("2024-01-02")
        val import = FakeImportHistoricalMarketDataUseCase()
        val service = LaorV4DashboardService(
            importHistoricalMarketDataUseCase = import,
            historicalMarketDataPort = FakeHistoricalMarketDataPort(
                listOf(candle("2024-01-02", close = "10.0")),
            ),
            marketCalendarPort = FakeMarketCalendarPort(
                listOf(
                    LocalDate.parse("2024-01-02"),
                    LocalDate.parse("2024-01-03"),
                ),
            ),
        )

        val response = service.calculate(
            CalculateLaorV4DashboardCommand(
                symbol = "TQQQ",
                startDate = startDate,
                asOfDate = startDate,
                initialCash = "1000".toBigDecimal(),
                totalSplitCount = 30,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
            ),
        )

        assertEquals(startDate, response.resolvedAsOfDate)
        assertDecimal("0.0", response.current.progressRound)
        assertEquals(0, response.current.holdingQuantity)
        assertDecimal("1000", response.current.cash)
        assertEquals(LocalDate.parse("2024-01-03"), response.nextOrderContext.orderSessionDate)
        assertEquals(listOf("FIRST_BUY"), response.nextOrders.map { it.orderTag })
        assertDecimal("10.0", response.nextOrderContext.previousClose)
        assertEquals(startDate.minusDays(14), import.commands.single().from)
        assertEquals(startDate, import.commands.single().to)
    }

    @Test
    fun `returns paged trades and daily flow from dashboard replay`() {
        val startDate = LocalDate.parse("2024-01-02")
        val asOfDate = LocalDate.parse("2024-01-04")
        val service = LaorV4DashboardService(
            importHistoricalMarketDataUseCase = FakeImportHistoricalMarketDataUseCase(),
            historicalMarketDataPort = FakeHistoricalMarketDataPort(
                listOf(
                    candle("2024-01-02", close = "10.0"),
                    candle("2024-01-03", close = "10.5"),
                    candle("2024-01-04", close = "10.7"),
                ),
            ),
            marketCalendarPort = FakeMarketCalendarPort(
                listOf(
                    LocalDate.parse("2024-01-02"),
                    LocalDate.parse("2024-01-03"),
                    LocalDate.parse("2024-01-04"),
                    LocalDate.parse("2024-01-05"),
                ),
            ),
        )
        val command = CalculateLaorV4DashboardCommand(
            symbol = "TQQQ",
            startDate = startDate,
            asOfDate = asOfDate,
            initialCash = "1000".toBigDecimal(),
            totalSplitCount = 30,
            firstBuyLimitPercentAbovePreviousClose = 12.0,
        )

        val trades = service.findTrades(
            FindLaorV4DashboardTradesQuery(
                command = command,
                page = 0,
                size = 10,
            ),
        )
        val secondDailyFlowPage = service.findDailyFlow(
            FindLaorV4DashboardDailyFlowQuery(
                command = command,
                page = 1,
                size = 1,
                sort = LaorV4DashboardDetailSort.ASC,
            ),
        )

        assertEquals(asOfDate, trades.resolvedAsOfDate)
        assertEquals(2, trades.total)
        assertEquals("FIRST_BUY", trades.items.first().orderTag)
        assertEquals(LocalDate.parse("2024-01-03"), trades.items.first().date)
        assertEquals(2, secondDailyFlowPage.total)
        val flow = secondDailyFlowPage.items.single()
        assertEquals(LocalDate.parse("2024-01-04"), flow.date)
        assertEquals(LocalDate.parse("2024-01-03"), flow.referenceDate)
        assertDecimal("10.5", flow.previousClose)
        assertTrue(flow.orders.isNotEmpty())
        assertEquals(2, flow.before.holdingQuantity)
    }

    @Test
    fun `returns lightweight chart compatible LAOR index series`() {
        val startDate = LocalDate.parse("2024-01-02")
        val asOfDate = LocalDate.parse("2024-01-03")
        val service = LaorV4DashboardService(
            importHistoricalMarketDataUseCase = FakeImportHistoricalMarketDataUseCase(),
            historicalMarketDataPort = FakeHistoricalMarketDataPort(
                listOf(
                    candle("2024-01-02", close = "10.0"),
                    candle("2024-01-03", close = "10.5"),
                ),
            ),
            marketCalendarPort = FakeMarketCalendarPort(
                listOf(
                    LocalDate.parse("2024-01-02"),
                    LocalDate.parse("2024-01-03"),
                    LocalDate.parse("2024-01-04"),
                ),
            ),
        )

        val response = service.findIndex(
            FindLaorV4DashboardIndexQuery(
                command = CalculateLaorV4DashboardCommand(
                    symbol = "TQQQ",
                    startDate = startDate,
                    asOfDate = asOfDate,
                    initialCash = "1000".toBigDecimal(),
                    totalSplitCount = 30,
                    firstBuyLimitPercentAbovePreviousClose = 12.0,
                ),
            ),
        )

        assertEquals(startDate, response.base.baseDate)
        assertDecimal("100", response.base.baseValue)
        assertEquals("TQQQ", response.base.benchmarkSymbol)
        assertEquals(listOf("laorIndex", "benchmarkIndex"), response.series.map { it.key })
        assertEquals("LineSeries", response.series.first().chartType)
        assertEquals(startDate, response.series.first().data.first().time)
        assertDecimal("100", response.series.first().data.first().value)
        assertEquals(2, response.points.size)
        assertEquals(true, response.points.first().basePoint)
        assertEquals(asOfDate, response.points.last().date)
        assertDecimal("105.00", response.points.last().benchmarkIndex)
    }

    @Test
    fun `returns LAOR index for all supported symbols and split counts`() {
        val startDate = LocalDate.parse("2024-01-02")
        val asOfDate = LocalDate.parse("2024-01-03")
        val cases = listOf("TQQQ", "SOXL").flatMap { symbol ->
            listOf(20, 30, 40).map { splitCount -> symbol to splitCount }
        }

        cases.forEach { (symbol, splitCount) ->
            val service = LaorV4DashboardService(
                importHistoricalMarketDataUseCase = FakeImportHistoricalMarketDataUseCase(),
                historicalMarketDataPort = FakeHistoricalMarketDataPort(
                    listOf(
                        candle(startDate, close = "10.0"),
                        candle(asOfDate, close = "10.5"),
                    ),
                ),
                marketCalendarPort = FakeMarketCalendarPort(
                    listOf(
                        startDate,
                        asOfDate,
                        asOfDate.plusDays(1),
                    ),
                ),
            )

            val response = service.findIndex(
                FindLaorV4DashboardIndexQuery(
                    command = CalculateLaorV4DashboardCommand(
                        symbol = symbol,
                        startDate = startDate,
                        asOfDate = asOfDate,
                        initialCash = "1000".toBigDecimal(),
                        totalSplitCount = splitCount,
                        firstBuyLimitPercentAbovePreviousClose = 12.0,
                    ),
                ),
            )

            assertEquals(symbol, response.symbol)
            assertEquals(symbol, response.base.benchmarkSymbol)
            assertEquals(listOf("LAOR $symbol", "$symbol Buy & Hold"), response.series.map { it.label })
            assertEquals(2, response.points.size)
            assertDecimal("100", response.points.first().laorIndex)
            assertDecimal("105.00", response.points.last().benchmarkIndex)
        }
    }

    @Test
    fun `rejects invalid detail pagination`() {
        val service = LaorV4DashboardService(
            importHistoricalMarketDataUseCase = FakeImportHistoricalMarketDataUseCase(),
            historicalMarketDataPort = FakeHistoricalMarketDataPort(emptyList()),
            marketCalendarPort = FakeMarketCalendarPort(emptyList()),
        )

        assertFailsWith<IllegalArgumentException> {
            service.findTrades(
                FindLaorV4DashboardTradesQuery(
                    command = CalculateLaorV4DashboardCommand(
                        symbol = "TQQQ",
                        startDate = LocalDate.parse("2024-01-02"),
                        asOfDate = LocalDate.parse("2024-01-03"),
                        initialCash = "1000".toBigDecimal(),
                        totalSplitCount = 20,
                        firstBuyLimitPercentAbovePreviousClose = 12.0,
                    ),
                    page = 0,
                    size = 1001,
                ),
            )
        }
    }

    @Test
    fun `resolves future as-of date to latest cached candle when current daily candle is missing`() {
        val startDate = LocalDate.now().minusDays(3)
        val latestCandleDate = startDate.plusDays(1)
        val requestedAsOfDate = LocalDate.now().plusDays(1)
        val import = FakeImportHistoricalMarketDataUseCase(
            exception = IllegalArgumentException("historical candles missing for trading days: missing=[${LocalDate.now()}]"),
        )
        val service = LaorV4DashboardService(
            importHistoricalMarketDataUseCase = import,
            historicalMarketDataPort = FakeHistoricalMarketDataPort(
                listOf(
                    candle(startDate, close = "10.0"),
                    candle(latestCandleDate, close = "10.5"),
                ),
            ),
            marketCalendarPort = FakeMarketCalendarPort(
                listOf(startDate, latestCandleDate, latestCandleDate.plusDays(1)),
            ),
        )

        val response = service.calculate(
            CalculateLaorV4DashboardCommand(
                symbol = "TQQQ",
                startDate = startDate,
                asOfDate = requestedAsOfDate,
                initialCash = "1000".toBigDecimal(),
                totalSplitCount = 20,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
            ),
        )

        assertEquals(requestedAsOfDate, response.requestedAsOfDate)
        assertEquals(latestCandleDate, response.resolvedAsOfDate)
        assertEquals(requestedAsOfDate, import.commands.single().to)
    }

    @Test
    fun `fails future as-of fallback when cached data has an earlier replay gap`() {
        val startDate = LocalDate.now().minusDays(4)
        val missingDate = startDate.plusDays(1)
        val latestCandleDate = startDate.plusDays(2)
        val requestedAsOfDate = LocalDate.now().plusDays(1)
        val import = FakeImportHistoricalMarketDataUseCase(
            exception = IllegalArgumentException("historical candles missing for trading days: missing=[$missingDate]"),
        )
        val service = LaorV4DashboardService(
            importHistoricalMarketDataUseCase = import,
            historicalMarketDataPort = FakeHistoricalMarketDataPort(
                listOf(
                    candle(startDate, close = "10.0"),
                    candle(latestCandleDate, close = "10.5"),
                ),
            ),
            marketCalendarPort = FakeMarketCalendarPort(
                listOf(startDate, missingDate, latestCandleDate, latestCandleDate.plusDays(1)),
            ),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            service.calculate(
                CalculateLaorV4DashboardCommand(
                    symbol = "TQQQ",
                    startDate = startDate,
                    asOfDate = requestedAsOfDate,
                    initialCash = "1000".toBigDecimal(),
                    totalSplitCount = 20,
                    firstBuyLimitPercentAbovePreviousClose = 12.0,
                ),
            )
        }

        assertEquals(
            true,
            exception.message.orEmpty().contains("historical candles missing for resolved replay window"),
        )
    }

    @Test
    fun `fails for unsupported split count`() {
        val service = LaorV4DashboardService(
            importHistoricalMarketDataUseCase = FakeImportHistoricalMarketDataUseCase(),
            historicalMarketDataPort = FakeHistoricalMarketDataPort(emptyList()),
            marketCalendarPort = FakeMarketCalendarPort(emptyList()),
        )

        assertFailsWith<IllegalArgumentException> {
            service.calculate(
                CalculateLaorV4DashboardCommand(
                    symbol = "TQQQ",
                    startDate = LocalDate.parse("2024-01-02"),
                    asOfDate = LocalDate.parse("2024-01-03"),
                    initialCash = "1000".toBigDecimal(),
                    totalSplitCount = 10,
                    firstBuyLimitPercentAbovePreviousClose = 12.0,
                ),
            )
        }
    }

    private class FakeImportHistoricalMarketDataUseCase(
        private val exception: RuntimeException? = null,
    ) : ImportHistoricalMarketDataUseCase {
        val commands = mutableListOf<ImportHistoricalMarketDataCommand>()

        override fun execute(command: ImportHistoricalMarketDataCommand): ImportHistoricalMarketDataResult {
            commands += command
            exception?.let { throw it }
            return ImportHistoricalMarketDataResult(
                symbol = command.symbol.trim().uppercase(),
                market = command.market.trim().uppercase(),
                importedCount = 0,
                from = command.from,
                to = command.to,
            )
        }
    }

    private class FakeHistoricalMarketDataPort(
        private val candles: List<HistoricalCandle>,
    ) : HistoricalMarketDataPort {
        override fun findDailyCandles(query: HistoricalDailyCandlesQuery): List<HistoricalCandle> {
            return candles
                .filter { it.date >= query.from && it.date <= query.to }
                .sortedBy { it.date }
        }

        override fun saveDailyCandles(command: SaveHistoricalDailyCandlesCommand) {
        }
    }

    private class FakeMarketCalendarPort(
        private val tradingDays: List<LocalDate>,
    ) : MarketCalendarPort {
        override fun findTradingDays(query: MarketTradingDaysQuery): MarketTradingDaysResult {
            return MarketTradingDaysResult(
                market = query.market,
                dates = tradingDays.filter { it >= query.from && it <= query.to },
                source = MarketCalendarSource.PANDAS_MARKET_CALENDARS,
            )
        }
    }

    private fun candle(
        date: String,
        close: String,
    ): HistoricalCandle {
        return candle(LocalDate.parse(date), close)
    }

    private fun candle(
        date: LocalDate,
        close: String,
    ): HistoricalCandle {
        return HistoricalCandle(
            symbol = "TQQQ",
            market = "US",
            date = date,
            open = close.toBigDecimal(),
            high = close.toBigDecimal(),
            low = close.toBigDecimal(),
            close = close.toBigDecimal(),
            adjustedClose = close.toBigDecimal(),
            volume = 1000,
            source = "TEST",
        )
    }

    private fun assertDecimal(expected: String, actual: java.math.BigDecimal) {
        val diff = expected.toBigDecimal().subtract(actual).abs()
        assertTrue(diff < "0.000000001".toBigDecimal(), "actual=$actual")
    }
}

package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.out.ExternalHistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.ExternalHistoricalMarketDataPort
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

class ImportHistoricalMarketDataServiceTest {
    @Test
    fun `imports candles from external source and saves them`() {
        val candle = candle("2024-01-02")
        val external = FakeExternalHistoricalMarketDataPort(listOf(candle))
        val store = FakeHistoricalMarketDataPort()
        val calendar = FakeMarketCalendarPort(listOf(LocalDate.parse("2024-01-02")))
        val service = ImportHistoricalMarketDataService(external, store, calendar)

        val result = service.execute(
            ImportHistoricalMarketDataCommand(
                symbol = "tqqq",
                from = LocalDate.parse("2024-01-01"),
                to = LocalDate.parse("2024-01-31"),
            ),
        )

        assertEquals("TQQQ", result.symbol)
        assertEquals(1, result.importedCount)
        assertEquals(listOf(candle), store.saved.single().candles)
        assertEquals(LocalDate.parse("2024-01-02"), external.queries.single().from)
        assertEquals(LocalDate.parse("2024-01-02"), external.queries.single().to)
    }

    @Test
    fun `uses cached candles when requested range is already covered`() {
        val cachedCandles = listOf(candle("2024-01-02"), candle("2024-01-03"))
        val external = FakeExternalHistoricalMarketDataPort(emptyList())
        val store = FakeHistoricalMarketDataPort(cachedCandles)
        val calendar = FakeMarketCalendarPort(
            listOf(LocalDate.parse("2024-01-02"), LocalDate.parse("2024-01-03")),
        )
        val service = ImportHistoricalMarketDataService(external, store, calendar)

        val result = service.execute(
            ImportHistoricalMarketDataCommand(
                symbol = "TQQQ",
                from = LocalDate.parse("2024-01-01"),
                to = LocalDate.parse("2024-01-03"),
            ),
        )

        assertEquals(0, result.importedCount)
        assertEquals(LocalDate.parse("2024-01-02"), result.from)
        assertEquals(LocalDate.parse("2024-01-03"), result.to)
        assertEquals(emptyList(), external.queries)
        assertEquals(emptyList(), store.saved)
    }

    @Test
    fun `fetches and saves only missing leading cache range`() {
        val cachedCandles = listOf(candle("2024-01-10"), candle("2024-01-11"))
        val fetchedCandles = listOf(
            candle("2024-01-02"),
            candle("2024-01-03"),
            candle("2024-01-04"),
            candle("2024-01-05"),
            candle("2024-01-08"),
            candle("2024-01-09"),
        )
        val external = FakeExternalHistoricalMarketDataPort(fetchedCandles)
        val store = FakeHistoricalMarketDataPort(cachedCandles)
        val calendar = FakeMarketCalendarPort(
            listOf(
                "2024-01-02",
                "2024-01-03",
                "2024-01-04",
                "2024-01-05",
                "2024-01-08",
                "2024-01-09",
                "2024-01-10",
                "2024-01-11",
            ).map(LocalDate::parse),
        )
        val service = ImportHistoricalMarketDataService(external, store, calendar)

        val result = service.execute(
            ImportHistoricalMarketDataCommand(
                symbol = "TQQQ",
                from = LocalDate.parse("2024-01-01"),
                to = LocalDate.parse("2024-01-11"),
            ),
        )

        assertEquals(6, result.importedCount)
        assertEquals(LocalDate.parse("2024-01-02"), external.queries.single().from)
        assertEquals(LocalDate.parse("2024-01-09"), external.queries.single().to)
        assertEquals(fetchedCandles, store.saved.single().candles)
    }

    @Test
    fun `continues with cached candles when external source is unavailable`() {
        val cachedCandles = listOf(candle("2024-01-02"))
        val external = FakeExternalHistoricalMarketDataPort(
            candles = emptyList(),
            exception = RuntimeException("sidecar unavailable"),
        )
        val store = FakeHistoricalMarketDataPort(cachedCandles)
        val calendar = FakeMarketCalendarPort(
            listOf(
                LocalDate.parse("2024-01-02"),
                LocalDate.parse("2024-01-03"),
            ),
        )
        val service = ImportHistoricalMarketDataService(external, store, calendar)

        val exception = assertFailsWith<IllegalArgumentException> {
            service.execute(
                ImportHistoricalMarketDataCommand(
                    symbol = "TQQQ",
                    from = LocalDate.parse("2024-01-02"),
                    to = LocalDate.parse("2024-01-03"),
                ),
            )
        }

        assertEquals(1, external.queries.size)
        assertEquals(true, exception.message.orEmpty().contains("historical candles missing for trading days"))
    }

    @Test
    fun `fails when external source returns no candles for expected trading days`() {
        val tradingDay = LocalDate.parse("2024-01-02")
        val service = ImportHistoricalMarketDataService(
            FakeExternalHistoricalMarketDataPort(emptyList()),
            FakeHistoricalMarketDataPort(),
            FakeMarketCalendarPort(listOf(tradingDay)),
        )

        assertFailsWith<IllegalArgumentException> {
            service.execute(
                ImportHistoricalMarketDataCommand(
                    symbol = "TQQQ",
                    from = LocalDate.parse("2024-01-01"),
                    to = LocalDate.parse("2024-01-31"),
                ),
            )
        }
    }

    @Test
    fun `fails without calling external source when requested range has no trading days`() {
        val external = FakeExternalHistoricalMarketDataPort(emptyList())
        val service = ImportHistoricalMarketDataService(
            external,
            FakeHistoricalMarketDataPort(),
            FakeMarketCalendarPort(emptyList()),
        )

        assertFailsWith<IllegalArgumentException> {
            service.execute(
                ImportHistoricalMarketDataCommand(
                    symbol = "TQQQ",
                    from = LocalDate.parse("2024-01-06"),
                    to = LocalDate.parse("2024-01-07"),
                ),
            )
        }
        assertEquals(emptyList(), external.queries)
    }

    private fun candle(date: String): HistoricalCandle {
        return HistoricalCandle(
            symbol = "TQQQ",
            market = "US",
            date = LocalDate.parse(date),
            open = "10.0".toBigDecimal(),
            high = "11.0".toBigDecimal(),
            low = "9.0".toBigDecimal(),
            close = "10.5".toBigDecimal(),
            adjustedClose = "10.5".toBigDecimal(),
            volume = 1000,
            source = "YFINANCE",
        )
    }

    private class FakeExternalHistoricalMarketDataPort(
        private val candles: List<HistoricalCandle>,
        private val exception: RuntimeException? = null,
    ) : ExternalHistoricalMarketDataPort {
        val queries = mutableListOf<ExternalHistoricalDailyCandlesQuery>()

        override fun fetchDailyCandles(query: ExternalHistoricalDailyCandlesQuery): List<HistoricalCandle> {
            queries += query
            exception?.let { throw it }
            return candles
        }
    }

    private class FakeHistoricalMarketDataPort(
        initialCandles: List<HistoricalCandle> = emptyList(),
    ) : HistoricalMarketDataPort {
        private val candles = initialCandles.toMutableList()
        val saved = mutableListOf<SaveHistoricalDailyCandlesCommand>()

        override fun findDailyCandles(query: HistoricalDailyCandlesQuery): List<HistoricalCandle> {
            return candles
                .filter { it.date >= query.from && it.date <= query.to }
                .sortedBy { it.date }
        }

        override fun saveDailyCandles(command: SaveHistoricalDailyCandlesCommand) {
            saved += command
            candles += command.candles
        }
    }

    private class FakeMarketCalendarPort(
        private val tradingDays: List<LocalDate>,
    ) : MarketCalendarPort {
        val queries = mutableListOf<MarketTradingDaysQuery>()

        override fun findTradingDays(query: MarketTradingDaysQuery): MarketTradingDaysResult {
            queries += query
            return MarketTradingDaysResult(
                market = query.market,
                dates = tradingDays.filter { it >= query.from && it <= query.to },
                source = MarketCalendarSource.PANDAS_MARKET_CALENDARS,
            )
        }
    }
}

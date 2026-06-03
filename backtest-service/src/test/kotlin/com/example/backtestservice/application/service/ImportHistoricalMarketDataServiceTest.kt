package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.out.ExternalHistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.ExternalHistoricalMarketDataPort
import com.example.backtestservice.application.port.out.HistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.HistoricalMarketDataPort
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
        val service = ImportHistoricalMarketDataService(external, store)

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
    }

    @Test
    fun `fails when external source returns no candles`() {
        val service = ImportHistoricalMarketDataService(
            FakeExternalHistoricalMarketDataPort(emptyList()),
            FakeHistoricalMarketDataPort(),
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
    ) : ExternalHistoricalMarketDataPort {
        override fun fetchDailyCandles(query: ExternalHistoricalDailyCandlesQuery): List<HistoricalCandle> {
            return candles
        }
    }

    private class FakeHistoricalMarketDataPort : HistoricalMarketDataPort {
        val saved = mutableListOf<SaveHistoricalDailyCandlesCommand>()

        override fun findDailyCandles(query: HistoricalDailyCandlesQuery): List<HistoricalCandle> {
            return emptyList()
        }

        override fun saveDailyCandles(command: SaveHistoricalDailyCandlesCommand) {
            saved += command
        }
    }
}

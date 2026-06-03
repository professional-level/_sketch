package com.example.backtestservice.adapter.out.marketdata

import com.example.backtestservice.application.port.out.HistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.SaveHistoricalDailyCandlesCommand
import com.example.backtestservice.domain.market.HistoricalCandle
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class CsvHistoricalMarketDataAdapterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `loads yfinance daily candles from normalized csv`() {
        tempDir.resolve("TQQQ.csv").writeText(
            """
            date,open,high,low,close,adj_close,dividend,volume
            2024-01-02,10.1,11.2,9.9,10.8,10.7,0,12345
            2024-01-03,10.8,12.0,10.5,11.5,11.4,0.15,23456
            """.trimIndent(),
        )
        val adapter = CsvHistoricalMarketDataAdapter(tempDir.toString())

        val candles = adapter.findDailyCandles(
            HistoricalDailyCandlesQuery(
                symbol = "tqqq",
                from = LocalDate.parse("2024-01-03"),
                to = LocalDate.parse("2024-01-03"),
            ),
        )

        assertEquals(1, candles.size)
        with(candles.single()) {
            assertEquals("TQQQ", symbol)
            assertEquals("US", market)
            assertEquals(LocalDate.parse("2024-01-03"), date)
            assertEquals("10.8".toBigDecimal(), open)
            assertEquals("12.0".toBigDecimal(), high)
            assertEquals("10.5".toBigDecimal(), low)
            assertEquals("11.5".toBigDecimal(), close)
            assertEquals("11.4".toBigDecimal(), adjustedClose)
            assertEquals("0.15".toBigDecimal(), dividend)
            assertEquals(23456, volume)
            assertEquals("YFINANCE", source)
        }
    }

    @Test
    fun `returns empty candles when ticker csv does not exist`() {
        val adapter = CsvHistoricalMarketDataAdapter(tempDir.toString())

        val candles = adapter.findDailyCandles(
            HistoricalDailyCandlesQuery(
                symbol = "missing",
                from = LocalDate.parse("2024-01-01"),
                to = LocalDate.parse("2024-01-31"),
            ),
        )

        assertEquals(emptyList(), candles)
    }

    @Test
    fun `saves daily candles to normalized csv`() {
        val adapter = CsvHistoricalMarketDataAdapter(tempDir.toString())

        adapter.saveDailyCandles(
            SaveHistoricalDailyCandlesCommand(
                symbol = "tqqq",
                candles = listOf(
                    HistoricalCandle(
                        symbol = "TQQQ",
                        market = "US",
                        date = LocalDate.parse("2024-01-02"),
                        open = "10.1".toBigDecimal(),
                        high = "11.2".toBigDecimal(),
                        low = "9.9".toBigDecimal(),
                        close = "10.8".toBigDecimal(),
                        adjustedClose = "10.7".toBigDecimal(),
                        dividend = "0.12".toBigDecimal(),
                        volume = 12345,
                        source = "YFINANCE",
                    ),
                ),
            ),
        )

        val candles = adapter.findDailyCandles(
            HistoricalDailyCandlesQuery(
                symbol = "TQQQ",
                from = LocalDate.parse("2024-01-01"),
                to = LocalDate.parse("2024-01-31"),
            ),
        )

        assertEquals(1, candles.size)
        assertEquals("10.8".toBigDecimal(), candles.single().close)
        assertEquals("0.12".toBigDecimal(), candles.single().dividend)
    }

    @Test
    fun `merges saved candles with existing csv by date`() {
        tempDir.resolve("TQQQ.csv").writeText(
            """
            date,open,high,low,close,adj_close,dividend,volume
            2024-01-02,10.1,11.2,9.9,10.8,10.7,0,12345
            2024-01-03,10.8,12.0,10.5,11.5,11.4,0.15,23456
            """.trimIndent(),
        )
        val adapter = CsvHistoricalMarketDataAdapter(tempDir.toString())

        adapter.saveDailyCandles(
            SaveHistoricalDailyCandlesCommand(
                symbol = "tqqq",
                candles = listOf(
                    HistoricalCandle(
                        symbol = "TQQQ",
                        market = "US",
                        date = LocalDate.parse("2024-01-03"),
                        open = "12.0".toBigDecimal(),
                        high = "13.0".toBigDecimal(),
                        low = "11.0".toBigDecimal(),
                        close = "12.5".toBigDecimal(),
                        adjustedClose = "12.4".toBigDecimal(),
                        dividend = "0.20".toBigDecimal(),
                        volume = 34567,
                        source = "YFINANCE",
                    ),
                    HistoricalCandle(
                        symbol = "TQQQ",
                        market = "US",
                        date = LocalDate.parse("2024-01-04"),
                        open = "12.5".toBigDecimal(),
                        high = "13.5".toBigDecimal(),
                        low = "12.0".toBigDecimal(),
                        close = "13.0".toBigDecimal(),
                        adjustedClose = "12.9".toBigDecimal(),
                        dividend = "0".toBigDecimal(),
                        volume = 45678,
                        source = "YFINANCE",
                    ),
                ),
            ),
        )

        val candles = adapter.findDailyCandles(
            HistoricalDailyCandlesQuery(
                symbol = "TQQQ",
                from = LocalDate.parse("2024-01-01"),
                to = LocalDate.parse("2024-01-31"),
            ),
        )

        assertEquals(3, candles.size)
        assertEquals("10.8".toBigDecimal(), candles[0].close)
        assertEquals("12.5".toBigDecimal(), candles[1].close)
        assertEquals("0.20".toBigDecimal(), candles[1].dividend)
        assertEquals("13.0".toBigDecimal(), candles[2].close)
    }
}

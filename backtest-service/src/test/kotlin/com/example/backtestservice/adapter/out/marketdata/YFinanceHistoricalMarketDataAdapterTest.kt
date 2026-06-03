package com.example.backtestservice.adapter.out.marketdata

import com.example.backtestservice.application.port.out.ExternalHistoricalDailyCandlesQuery
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YFinanceHistoricalMarketDataAdapterTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetches daily candles from yfinance sidecar`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "candles": {
                        "TQQQ": [
                          {
                            "date": "2024-01-02",
                            "open": "10.1",
                            "high": "11.2",
                            "low": "9.9",
                            "close": "10.8",
                            "adj_close": "10.7",
                            "dividend": "0.12",
                            "volume": 12345
                          },
                          {
                            "date": "2024-01-03",
                            "open": "10.8",
                            "high": "11.9",
                            "low": "10.5",
                            "close": "11.2",
                            "adj_close": "11.2",
                            "dividend": "nan",
                            "volume": 23456
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
        )
        val adapter = YFinanceHistoricalMarketDataAdapter(
            baseUrl = server.url("/").toString().trimEnd('/'),
            defaultTimeoutSeconds = 5,
        )

        val candles = adapter.fetchDailyCandles(
            ExternalHistoricalDailyCandlesQuery(
                symbol = "tqqq",
                from = LocalDate.parse("2024-01-02"),
                to = LocalDate.parse("2024-01-03"),
            ),
        )

        assertEquals(2, candles.size)
        assertEquals("TQQQ", candles.first().symbol)
        assertEquals("10.8".toBigDecimal(), candles.first().close)
        assertEquals("0.12".toBigDecimal(), candles.first().dividend)
        assertEquals("11.2".toBigDecimal(), candles.last().close)
        assertEquals("0".toBigDecimal(), candles.last().dividend)
        val request = server.takeRequest()
        assertEquals("/daily-candles", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"start\":\"2024-01-02\""))
        assertTrue(body.contains("\"end\":\"2024-01-04\""))
    }
}

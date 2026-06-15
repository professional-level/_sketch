package com.example.backtestservice.adapter.out.marketdata

import com.example.backtestservice.application.port.out.MarketCalendarSource
import com.example.backtestservice.application.port.out.MarketTradingDayQuery
import com.example.backtestservice.application.port.out.MarketTradingDaysQuery
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YFinanceMarketCalendarAdapterTest {
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
    fun `fetches valid trading days from yfinance sidecar market calendar`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "market": "US",
                      "calendar": "NYSE",
                      "start": "2024-01-01",
                      "end": "2024-01-05",
                      "valid_days": ["2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05"]
                    }
                    """.trimIndent(),
                ),
        )
        val adapter = adapter()

        val result = adapter.findTradingDays(
            MarketTradingDaysQuery(
                market = "us",
                from = LocalDate.parse("2024-01-01"),
                to = LocalDate.parse("2024-01-05"),
            ),
        )

        assertEquals(MarketCalendarSource.PANDAS_MARKET_CALENDARS, result.source)
        assertEquals(
            listOf("2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05").map(LocalDate::parse),
            result.dates,
        )
        val request = server.takeRequest()
        assertEquals("/market-calendar/valid-days", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"market\":\"US\""))
        assertTrue(body.contains("\"start\":\"2024-01-01\""))
        assertTrue(body.contains("\"end\":\"2024-01-05\""))
    }

    @Test
    fun `checks a single trading day through sidecar calendar`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "market": "US",
                      "calendar": "NYSE",
                      "start": "2024-01-01",
                      "end": "2024-01-01",
                      "valid_days": []
                    }
                    """.trimIndent(),
                ),
        )
        val adapter = adapter()

        val result = adapter.isTradingDay(
            MarketTradingDayQuery(
                market = "US",
                date = LocalDate.parse("2024-01-01"),
            ),
        )

        assertEquals(MarketCalendarSource.PANDAS_MARKET_CALENDARS, result.source)
        assertFalse(result.tradingDay)
    }

    @Test
    fun `falls back to built in us equity calendar when sidecar calendar fails`() {
        server.enqueue(MockResponse().setResponseCode(502))
        val adapter = adapter()

        val result = adapter.findTradingDays(
            MarketTradingDaysQuery(
                market = "US",
                from = LocalDate.parse("2026-07-03"),
                to = LocalDate.parse("2026-07-06"),
            ),
        )

        assertEquals(MarketCalendarSource.US_EQUITY_MARKET_CALENDAR_FALLBACK, result.source)
        assertEquals(listOf(LocalDate.parse("2026-07-06")), result.dates)
    }

    private fun adapter(): YFinanceMarketCalendarAdapter {
        return YFinanceMarketCalendarAdapter(
            baseUrl = server.url("/").toString().trimEnd('/'),
            defaultTimeoutSeconds = 5,
            maxInMemoryBytes = 1024 * 1024,
        )
    }
}

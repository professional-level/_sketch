package com.example.strategyexecutionservice.adapter.out.calendar

import com.example.strategyexecutionservice.application.port.out.TradingMarket
import com.example.strategyexecutionservice.config.calendar.TradingCalendarProperties
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfiguredTradingCalendarAdapterTest {

    @Test
    fun `closes weekends and configured holidays for us market`() {
        val properties = TradingCalendarProperties().apply {
            us.holidays = listOf("2026-07-03")
        }
        val adapter = ConfiguredTradingCalendarAdapter(properties)

        assertFalse(adapter.isTradingDay(TradingMarket.US, LocalDate.parse("2026-07-03")))
        assertFalse(adapter.isTradingDay(TradingMarket.US, LocalDate.parse("2026-07-04")))
        assertTrue(adapter.isTradingDay(TradingMarket.US, LocalDate.parse("2026-07-06")))
    }

    @Test
    fun `can be disabled when an external calendar owns trading day decisions`() {
        val properties = TradingCalendarProperties().apply {
            us.enabled = false
            us.holidays = listOf("2026-07-03")
        }
        val adapter = ConfiguredTradingCalendarAdapter(properties)

        assertTrue(adapter.isTradingDay(TradingMarket.US, LocalDate.parse("2026-07-03")))
    }
}

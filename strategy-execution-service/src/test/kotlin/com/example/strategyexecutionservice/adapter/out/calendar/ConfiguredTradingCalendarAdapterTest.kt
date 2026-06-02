package com.example.strategyexecutionservice.adapter.out.calendar

import com.example.strategyexecutionservice.application.port.out.TradingMarket
import com.example.strategyexecutionservice.config.calendar.TradingCalendarProperties
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `closes default us market holidays`() {
        val adapter = ConfiguredTradingCalendarAdapter(TradingCalendarProperties())

        assertFalse(adapter.isTradingDay(TradingMarket.US, LocalDate.parse("2026-07-03")))
        assertFalse(adapter.isTradingDay(TradingMarket.US, LocalDate.parse("2026-04-03")))
        assertTrue(adapter.isTradingDay(TradingMarket.US, LocalDate.parse("2026-07-06")))
    }

    @Test
    fun `reports default us early close time`() {
        val adapter = ConfiguredTradingCalendarAdapter(TradingCalendarProperties())

        assertEquals(
            LocalTime.parse("13:00"),
            adapter.earlyCloseTime(TradingMarket.US, LocalDate.parse("2026-11-27")),
        )
        assertNull(adapter.earlyCloseTime(TradingMarket.US, LocalDate.parse("2026-11-30")))
    }

    @Test
    fun `uses configured early close time for listed dates`() {
        val properties = TradingCalendarProperties().apply {
            us.earlyCloseDays = listOf("2026-11-27")
            us.earlyCloseTime = "12:45"
        }
        val adapter = ConfiguredTradingCalendarAdapter(properties)

        assertEquals(
            LocalTime.parse("12:45"),
            adapter.earlyCloseTime(TradingMarket.US, LocalDate.parse("2026-11-27")),
        )
    }

    @Test
    fun `uses date-specific early close override before common early close time`() {
        val properties = TradingCalendarProperties().apply {
            us.earlyCloseDays = listOf("2026-11-27")
            us.earlyCloseTime = "13:00"
            us.earlyCloseTimes["2026-11-27"] = "12:30"
        }
        val adapter = ConfiguredTradingCalendarAdapter(properties)

        assertEquals(
            LocalTime.parse("12:30"),
            adapter.earlyCloseTime(TradingMarket.US, LocalDate.parse("2026-11-27")),
        )
    }

    @Test
    fun `converts requested timestamp to configured market date`() {
        val adapter = ConfiguredTradingCalendarAdapter(TradingCalendarProperties())

        val date = adapter.tradingDate(
            TradingMarket.US,
            ZonedDateTime.parse("2026-07-03T09:00:00+09:00[Asia/Seoul]"),
        )

        assertEquals(LocalDate.parse("2026-07-02"), date)
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

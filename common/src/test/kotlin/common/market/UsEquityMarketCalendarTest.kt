package com.example.common.market

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsEquityMarketCalendarTest {

    @Test
    fun `recognizes observed market holidays`() {
        assertFalse(UsEquityMarketCalendar.isTradingDay(LocalDate.parse("2026-07-03")))
        assertFalse(UsEquityMarketCalendar.isTradingDay(LocalDate.parse("2026-04-03")))
        assertFalse(UsEquityMarketCalendar.isTradingDay(LocalDate.parse("2027-06-18")))
    }

    @Test
    fun `recognizes standard early close days`() {
        assertEquals(
            LocalTime.parse("13:00"),
            UsEquityMarketCalendar.earlyCloseTime(LocalDate.parse("2026-11-27")),
        )
        assertEquals(
            LocalTime.parse("13:00"),
            UsEquityMarketCalendar.earlyCloseTime(LocalDate.parse("2026-12-24")),
        )
        assertEquals(
            LocalTime.parse("13:00"),
            UsEquityMarketCalendar.earlyCloseTime(LocalDate.parse("2028-07-03")),
        )
    }

    @Test
    fun `does not mark full holidays as early close sessions`() {
        assertNull(UsEquityMarketCalendar.earlyCloseTime(LocalDate.parse("2026-07-03")))
        assertNull(UsEquityMarketCalendar.earlyCloseTime(LocalDate.parse("2027-12-24")))
    }

    @Test
    fun `keeps ordinary weekdays open`() {
        assertTrue(UsEquityMarketCalendar.isTradingDay(LocalDate.parse("2026-07-06")))
        assertNull(UsEquityMarketCalendar.earlyCloseTime(LocalDate.parse("2026-07-06")))
    }
}

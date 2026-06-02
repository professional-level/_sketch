package com.example.common.market

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId

object UsEquityMarketCalendar {
    val zoneId: ZoneId = ZoneId.of("America/New_York")
    val regularOpen: LocalTime = LocalTime.of(9, 30)
    val regularClose: LocalTime = LocalTime.of(16, 0)
    val standardEarlyClose: LocalTime = LocalTime.of(13, 0)

    fun isTradingDay(date: LocalDate): Boolean {
        return date.dayOfWeek !in CLOSED_WEEKDAYS && !isMarketHoliday(date)
    }

    fun isMarketHoliday(date: LocalDate): Boolean {
        return marketHolidays(date.year).contains(date)
    }

    fun earlyCloseTime(date: LocalDate): LocalTime? {
        if (!isTradingDay(date)) return null
        return standardEarlyClose.takeIf {
            date == dayAfterThanksgiving(date.year) ||
                date == independenceDayEarlyClose(date.year) ||
                date == christmasEveEarlyClose(date.year)
        }
    }

    fun marketHolidays(year: Int): Set<LocalDate> {
        return buildSet {
            observedNewYearsDay(year)?.let(::add)
            add(nthDayOfWeek(year, Month.JANUARY, DayOfWeek.MONDAY, 3))
            add(nthDayOfWeek(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3))
            add(easterSunday(year).minusDays(2))
            add(lastDayOfWeek(year, Month.MAY, DayOfWeek.MONDAY))
            if (year >= JUNETEENTH_MARKET_HOLIDAY_START_YEAR) {
                add(observedFixedHoliday(year, Month.JUNE, 19))
            }
            add(observedFixedHoliday(year, Month.JULY, 4))
            add(nthDayOfWeek(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1))
            add(thanksgiving(year))
            add(observedFixedHoliday(year, Month.DECEMBER, 25))
        }
    }

    private fun observedNewYearsDay(year: Int): LocalDate? {
        val date = LocalDate.of(year, Month.JANUARY, 1)
        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> null
            DayOfWeek.SUNDAY -> date.plusDays(1)
            else -> date
        }
    }

    private fun observedFixedHoliday(year: Int, month: Month, dayOfMonth: Int): LocalDate {
        val date = LocalDate.of(year, month, dayOfMonth)
        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> date.minusDays(1)
            DayOfWeek.SUNDAY -> date.plusDays(1)
            else -> date
        }
    }

    private fun thanksgiving(year: Int): LocalDate {
        return nthDayOfWeek(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4)
    }

    private fun dayAfterThanksgiving(year: Int): LocalDate {
        return thanksgiving(year).plusDays(1)
    }

    private fun independenceDayEarlyClose(year: Int): LocalDate? {
        val date = LocalDate.of(year, Month.JULY, 3)
        return date.takeIf(::isTradingDay)
    }

    private fun christmasEveEarlyClose(year: Int): LocalDate? {
        val date = LocalDate.of(year, Month.DECEMBER, 24)
        return date.takeIf(::isTradingDay)
    }

    private fun nthDayOfWeek(
        year: Int,
        month: Month,
        dayOfWeek: DayOfWeek,
        occurrence: Int,
    ): LocalDate {
        val firstDay = LocalDate.of(year, month, 1)
        val daysUntilMatch = Math.floorMod(dayOfWeek.value - firstDay.dayOfWeek.value, DAYS_PER_WEEK)
        return firstDay.plusDays(daysUntilMatch.toLong() + DAYS_PER_WEEK.toLong() * (occurrence - 1))
    }

    private fun lastDayOfWeek(
        year: Int,
        month: Month,
        dayOfWeek: DayOfWeek,
    ): LocalDate {
        val lastDay = LocalDate.of(year, month, month.length(LocalDate.of(year, month, 1).isLeapYear))
        val daysSinceMatch = Math.floorMod(lastDay.dayOfWeek.value - dayOfWeek.value, DAYS_PER_WEEK)
        return lastDay.minusDays(daysSinceMatch.toLong())
    }

    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    private const val DAYS_PER_WEEK = 7
    private const val JUNETEENTH_MARKET_HOLIDAY_START_YEAR = 2022
    private val CLOSED_WEEKDAYS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}

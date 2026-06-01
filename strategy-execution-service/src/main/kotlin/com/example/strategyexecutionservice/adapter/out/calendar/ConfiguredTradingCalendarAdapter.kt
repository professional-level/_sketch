package com.example.strategyexecutionservice.adapter.out.calendar

import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.out.TradingCalendarPort
import com.example.strategyexecutionservice.application.port.out.TradingMarket
import com.example.strategyexecutionservice.config.calendar.TradingCalendarProperties
import java.time.DayOfWeek
import java.time.LocalDate

@ExternalApiAdapter
internal class ConfiguredTradingCalendarAdapter(
    private val properties: TradingCalendarProperties,
) : TradingCalendarPort {

    override fun isTradingDay(market: TradingMarket, date: LocalDate): Boolean {
        return when (market) {
            TradingMarket.US -> properties.us.isTradingDay(date)
        }
    }

    private fun TradingCalendarProperties.MarketCalendar.isTradingDay(date: LocalDate): Boolean {
        if (!enabled) return true
        if (weekdaysOnly && date.dayOfWeek in CLOSED_WEEKDAYS) return false
        if (date in holidays.mapNotNull { it.toLocalDateOrNull() }.toSet()) return false
        return true
    }

    private fun String.toLocalDateOrNull(): LocalDate? {
        return runCatching { LocalDate.parse(trim()) }.getOrNull()
    }

    companion object {
        private val CLOSED_WEEKDAYS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

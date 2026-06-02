package com.example.strategyexecutionservice.adapter.out.calendar

import com.example.common.ExternalApiAdapter
import com.example.common.market.UsEquityMarketCalendar
import com.example.strategyexecutionservice.application.port.out.TradingCalendarPort
import com.example.strategyexecutionservice.application.port.out.TradingMarket
import com.example.strategyexecutionservice.config.calendar.TradingCalendarProperties
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@ExternalApiAdapter
internal class ConfiguredTradingCalendarAdapter(
    private val properties: TradingCalendarProperties,
) : TradingCalendarPort {

    override fun isTradingDay(market: TradingMarket, date: LocalDate): Boolean {
        return when (market) {
            TradingMarket.US -> properties.us.isTradingDay(date)
        }
    }

    override fun earlyCloseTime(market: TradingMarket, date: LocalDate): LocalTime? {
        return when (market) {
            TradingMarket.US -> properties.us.earlyCloseTime(date)
        }
    }

    override fun tradingDate(market: TradingMarket, requestedAt: ZonedDateTime): LocalDate {
        return when (market) {
            TradingMarket.US -> requestedAt.withZoneSameInstant(properties.us.zone()).toLocalDate()
        }
    }

    override fun orderSessionDate(market: TradingMarket, requestedAt: ZonedDateTime): LocalDate {
        return when (market) {
            TradingMarket.US -> properties.us.orderSessionDate(requestedAt)
        }
    }

    private fun TradingCalendarProperties.MarketCalendar.isTradingDay(date: LocalDate): Boolean {
        if (!enabled) return true
        if (defaultUsEquityCalendarEnabled && !UsEquityMarketCalendar.isTradingDay(date)) return false
        if (!defaultUsEquityCalendarEnabled && weekdaysOnly && date.dayOfWeek in CLOSED_WEEKDAYS) return false
        if (date in holidays.mapNotNull { it.toLocalDateOrNull() }.toSet()) return false
        return true
    }

    private fun TradingCalendarProperties.MarketCalendar.earlyCloseTime(date: LocalDate): LocalTime? {
        if (!enabled || !isTradingDay(date)) return null
        val dateOverride = earlyCloseTimes[date.toString()]?.toLocalTimeOrNull()
        if (dateOverride != null) {
            return dateOverride
        }
        if (date in earlyCloseDays.mapNotNull { it.toLocalDateOrNull() }.toSet()) {
            return earlyCloseTime?.toLocalTimeOrNull() ?: UsEquityMarketCalendar.standardEarlyClose
        }
        return if (defaultUsEquityCalendarEnabled) {
            UsEquityMarketCalendar.earlyCloseTime(date)
        } else {
            null
        }
    }

    private fun TradingCalendarProperties.MarketCalendar.orderSessionDate(requestedAt: ZonedDateTime): LocalDate {
        val marketDateTime = requestedAt.withZoneSameInstant(zone())
        val localDate = marketDateTime.toLocalDate()
        val sessionClose = earlyCloseTime(localDate) ?: UsEquityMarketCalendar.regularClose
        return if (!isTradingDay(localDate) || !marketDateTime.toLocalTime().isBefore(sessionClose)) {
            localDate.plusDays(1)
        } else {
            localDate
        }
    }

    private fun TradingCalendarProperties.MarketCalendar.zone(): ZoneId {
        return runCatching { ZoneId.of(zoneId.trim()) }.getOrDefault(UsEquityMarketCalendar.zoneId)
    }

    private fun String.toLocalDateOrNull(): LocalDate? {
        return runCatching { LocalDate.parse(trim()) }.getOrNull()
    }

    private fun String.toLocalTimeOrNull(): LocalTime? {
        return runCatching { LocalTime.parse(trim()) }.getOrNull()
    }

    companion object {
        private val CLOSED_WEEKDAYS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

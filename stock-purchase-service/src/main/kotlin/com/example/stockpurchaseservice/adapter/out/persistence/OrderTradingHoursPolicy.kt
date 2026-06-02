package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.common.market.UsEquityMarketCalendar
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentCommand
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal class OrderTradingHoursPolicy(
    private val properties: OrderRiskProperties.TradingHoursProperties,
) {
    fun rejectReason(command: OrderRiskAssessmentCommand): String? {
        if (!properties.enabled) return null

        val window = command.market.tradingWindow()
        if (!window.enabled) return null

        val zone = window.zoneId.toZoneIdOrNull()
            ?: return "invalid trading-hours zone for ${command.market}: ${window.zoneId}"
        val localRequestedAt = command.createdAt.withZoneSameInstant(zone)
        val localDate = localRequestedAt.toLocalDate()
        val localTime = localRequestedAt.toLocalTime()

        if (window.weekdaysOnly && localDate.dayOfWeek in CLOSED_WEEKDAYS) {
            return "order blocked on ${command.market} non-trading weekday: date=$localDate"
        }
        if (window.isMarketHoliday(localDate)) {
            return "order blocked on ${command.market} market holiday: date=$localDate"
        }

        val open = window.regularOpen.toLocalTimeOrNull()
            ?: return "invalid trading-hours open for ${command.market}: ${window.regularOpen}"
        val regularClose = window.regularClose.toLocalTimeOrNull()
            ?: return "invalid trading-hours close for ${command.market}: ${window.regularClose}"
        val close = window.effectiveClose(localDate, regularClose)
            ?: return "invalid trading-hours close for ${command.market}"
        val orderCutoff = window.orderCutoff(command.orderType, regularClose, close)
            ?: return "invalid trading-hours cutoff for ${command.market} ${command.orderType}"

        if (!orderCutoff.isAfter(open)) {
            return "invalid trading-hours window for ${command.market} ${command.orderType}: open=$open cutoff=$orderCutoff"
        }

        return if (localTime.isBefore(open) || !localTime.isBefore(orderCutoff)) {
            "order blocked outside ${command.market} ${command.orderType} order window: " +
                "localTime=$localTime zone=${zone.id} allowed=$open-$orderCutoff"
        } else {
            null
        }
    }

    private fun StockOrderMarket.tradingWindow(): OrderRiskProperties.MarketTradingHours {
        return when (this) {
            StockOrderMarket.DOMESTIC -> properties.domestic
            StockOrderMarket.OVERSEAS_US -> properties.overseasUs
        }
    }

    private fun OrderRiskProperties.MarketTradingHours.isMarketHoliday(date: LocalDate): Boolean {
        if (date in holidays.mapNotNull { it.toLocalDateOrNull() }.toSet()) return true
        return defaultUsEquityCalendarEnabled && UsEquityMarketCalendar.isMarketHoliday(date)
    }

    private fun OrderRiskProperties.MarketTradingHours.effectiveClose(
        date: LocalDate,
        regularCloseTime: LocalTime,
    ): LocalTime? {
        val configuredEarlyCloseTime = earlyCloseTimeFor(date)
        if (configuredEarlyCloseTime == null) {
            return if (defaultUsEquityCalendarEnabled) {
                UsEquityMarketCalendar.earlyCloseTime(date) ?: regularCloseTime
            } else {
                regularCloseTime
            }
        }
        if (configuredEarlyCloseTime.isNullOrBlank()) return null
        return configuredEarlyCloseTime.toLocalTimeOrNull()
    }

    private fun OrderRiskProperties.MarketTradingHours.earlyCloseTimeFor(date: LocalDate): String? {
        val dateKey = date.toString()
        if (earlyCloseTimes.containsKey(dateKey)) {
            return earlyCloseTimes[dateKey]
        }
        return earlyCloseTimeForListedDate(date)
    }

    private fun OrderRiskProperties.MarketTradingHours.earlyCloseTimeForListedDate(date: LocalDate): String? {
        val earlyCloseDates = earlyCloseDates.mapNotNull { it.toLocalDateOrNull() }.toSet()
        return earlyCloseTime.takeIf { date in earlyCloseDates }
    }

    private fun OrderRiskProperties.MarketTradingHours.orderCutoff(
        orderType: OrderIntentType,
        regularClose: LocalTime,
        close: LocalTime,
    ): LocalTime? {
        val configuredCutoffValue = when (orderType) {
            OrderIntentType.LIMIT -> null
            OrderIntentType.LOC -> locCutoff
            OrderIntentType.MOC -> mocCutoff
        }
        if (configuredCutoffValue.isNullOrBlank()) return close

        val configuredCutoff = configuredCutoffValue.toLocalTimeOrNull() ?: return null
        val cutoffOffset = Duration.between(configuredCutoff, regularClose)
        if (cutoffOffset.isNegative) return null
        return close.minus(cutoffOffset)
    }

    private fun String.toZoneIdOrNull(): ZoneId? {
        return runCatching { ZoneId.of(trim()) }.getOrNull()
    }

    private fun String.toLocalTimeOrNull(): LocalTime? {
        return runCatching { LocalTime.parse(trim()) }.getOrNull()
    }

    private fun String.toLocalDateOrNull(): LocalDate? {
        return runCatching { LocalDate.parse(trim()) }.getOrNull()
    }

    companion object {
        private val CLOSED_WEEKDAYS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentCommand
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import java.time.DayOfWeek
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
        if (localDate in window.holidayDates()) {
            return "order blocked on ${command.market} market holiday: date=$localDate"
        }

        val open = window.regularOpen.toLocalTimeOrNull()
            ?: return "invalid trading-hours open for ${command.market}: ${window.regularOpen}"
        val close = window.effectiveClose(localDate)
            ?: return "invalid trading-hours close for ${command.market}"
        val orderCutoff = window.orderCutoff(command.orderType, close)
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

    private fun OrderRiskProperties.MarketTradingHours.holidayDates(): Set<LocalDate> {
        return holidays.mapNotNull { it.toLocalDateOrNull() }.toSet()
    }

    private fun OrderRiskProperties.MarketTradingHours.effectiveClose(date: LocalDate): LocalTime? {
        val regularCloseTime = regularClose.toLocalTimeOrNull() ?: return null
        val earlyCloseDates = earlyCloseDates.mapNotNull { it.toLocalDateOrNull() }.toSet()
        if (date !in earlyCloseDates) return regularCloseTime
        val configuredEarlyCloseTime = earlyCloseTime
        if (configuredEarlyCloseTime.isNullOrBlank()) return null
        return configuredEarlyCloseTime.toLocalTimeOrNull()
    }

    private fun OrderRiskProperties.MarketTradingHours.orderCutoff(
        orderType: OrderIntentType,
        close: LocalTime,
    ): LocalTime? {
        val configuredCutoffValue = when (orderType) {
            OrderIntentType.LIMIT -> null
            OrderIntentType.LOC -> locCutoff
            OrderIntentType.MOC -> mocCutoff
        }
        if (configuredCutoffValue.isNullOrBlank()) return close

        val configuredCutoff = configuredCutoffValue.toLocalTimeOrNull() ?: return null
        return minOf(configuredCutoff, close)
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

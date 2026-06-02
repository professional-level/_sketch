package com.example.stockpurchaseservice.application.scheduler

import com.example.common.market.UsEquityMarketCalendar
import com.example.stockpurchaseservice.application.port.`in`.CreateSellOrdersByStrategyUseCase
import com.example.stockpurchaseservice.application.port.`in`.RecoverUnknownOrderSubmissionsUseCase
import com.example.stockpurchaseservice.application.port.`in`.ReconcileExecutionsUseCase
import com.example.stockpurchaseservice.application.port.`in`.SimulateStockPurchaseUseCase
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Component
internal class StockTradeScheduler(
    private val createSellOrdersByStrategyUseCase: CreateSellOrdersByStrategyUseCase,
    private val reconcileExecutionsUseCase: ReconcileExecutionsUseCase,
    private val recoverUnknownOrderSubmissionsUseCase: RecoverUnknownOrderSubmissionsUseCase,
    private val simulateStockPurchaseUseCase: SimulateStockPurchaseUseCase,
    private val scheduleGate: StockTradeScheduleGate,
) {
    @Scheduled(cron = "0 */1 * ? * MON-FRI") // TODO: Adjust scheduler frequency by sell strategy.
    suspend fun sellOrderByStrategies() {
        runIfTradingDay("sell-order-by-strategies") {
            createSellOrdersByStrategyUseCase.execute()
        }
    }

    @Scheduled(cron = "0 */1 * ? * MON-FRI") // TODO: Adjust scheduler frequency by sell strategy.
    suspend fun executionCheck() {
        runIfTradingDay("execution-check") {
            recoverUnknownOrderSubmissionsUseCase.execute()
            reconcileExecutionsUseCase.execute()
        }
    }

    @Scheduled(cron = "0 */1 * ? * MON-FRI")
    suspend fun simulateStockPurchase() {
        runIfTradingDay("simulate-stock-purchase") {
            simulateStockPurchaseUseCase.execute()
        }
    }

    private suspend fun runIfTradingDay(
        jobName: String,
        block: suspend () -> Unit,
    ) {
        if (!scheduleGate.shouldRunNow()) {
            log.info("Skipping stock trade scheduler job outside configured trading calendar: job={}", jobName)
            return
        }
        block()
    }

    companion object {
        private val log = LoggerFactory.getLogger(StockTradeScheduler::class.java)
    }
}

internal fun interface StockTradeScheduleGate {
    fun shouldRunNow(): Boolean
}

@Component
internal class TradingHoursStockTradeScheduleGate(
    private val properties: OrderRiskProperties,
) : StockTradeScheduleGate {

    override fun shouldRunNow(): Boolean {
        return shouldRunAt(ZonedDateTime.now())
    }

    internal fun shouldRunAt(now: ZonedDateTime): Boolean {
        val tradingHours = properties.tradingHours
        if (!tradingHours.enabled) return true

        val overseasUs = tradingHours.overseasUs
        if (!overseasUs.enabled) return true

        val zone = overseasUs.zoneId.toZoneIdOrNull() ?: return false
        val localDate = now.withZoneSameInstant(zone).toLocalDate()
        val localTime = now.withZoneSameInstant(zone).toLocalTime()
        if (overseasUs.weekdaysOnly && localDate.dayOfWeek in CLOSED_WEEKDAYS) return false
        if (overseasUs.isConfiguredHoliday(localDate)) return false
        if (overseasUs.defaultUsEquityCalendarEnabled && UsEquityMarketCalendar.isMarketHoliday(localDate)) {
            return false
        }

        val open = overseasUs.regularOpen.toLocalTimeOrNull() ?: return false
        val regularClose = overseasUs.regularClose.toLocalTimeOrNull() ?: return false
        val close = overseasUs.effectiveClose(localDate, regularClose) ?: return false
        if (!close.isAfter(open)) return false

        return !localTime.isBefore(open) && localTime.isBefore(close)
    }

    private fun OrderRiskProperties.MarketTradingHours.isConfiguredHoliday(date: LocalDate): Boolean {
        return date in holidays.mapNotNull { it.toLocalDateOrNull() }.toSet()
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
        val earlyCloseDates = earlyCloseDates.mapNotNull { it.toLocalDateOrNull() }.toSet()
        return earlyCloseTime.takeIf { date in earlyCloseDates }
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

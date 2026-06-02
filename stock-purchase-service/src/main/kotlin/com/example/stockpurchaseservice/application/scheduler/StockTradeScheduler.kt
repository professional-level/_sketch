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
        runIfOrderSubmissionWindow("sell-order-by-strategies") {
            createSellOrdersByStrategyUseCase.execute()
        }
    }

    @Scheduled(cron = "0 */1 * ? * MON-FRI") // TODO: Adjust scheduler frequency by sell strategy.
    suspend fun executionCheck() {
        runIfRecoveryTradingDate("execution-check") {
            recoverUnknownOrderSubmissionsUseCase.execute()
            reconcileExecutionsUseCase.execute()
        }
    }

    @Scheduled(cron = "0 */1 * ? * MON-FRI")
    suspend fun simulateStockPurchase() {
        runIfOrderSubmissionWindow("simulate-stock-purchase") {
            simulateStockPurchaseUseCase.execute()
        }
    }

    private suspend fun runIfOrderSubmissionWindow(
        jobName: String,
        block: suspend () -> Unit,
    ) {
        if (!scheduleGate.shouldRunOrderSubmissionNow()) {
            log.info("Skipping stock trade scheduler job outside configured order window: job={}", jobName)
            return
        }
        block()
    }

    private suspend fun runIfRecoveryTradingDate(
        jobName: String,
        block: suspend () -> Unit,
    ) {
        if (!scheduleGate.shouldRunRecoveryNow()) {
            log.info("Skipping stock trade scheduler recovery job outside configured trading date: job={}", jobName)
            return
        }
        block()
    }

    companion object {
        private val log = LoggerFactory.getLogger(StockTradeScheduler::class.java)
    }
}

internal fun interface StockTradeScheduleGate {
    fun shouldRunOrderSubmissionNow(): Boolean

    fun shouldRunRecoveryNow(): Boolean = shouldRunOrderSubmissionNow()
}

@Component
internal class TradingHoursStockTradeScheduleGate(
    private val properties: OrderRiskProperties,
) : StockTradeScheduleGate {

    override fun shouldRunOrderSubmissionNow(): Boolean {
        return shouldRunOrderSubmissionAt(ZonedDateTime.now())
    }

    override fun shouldRunRecoveryNow(): Boolean {
        return shouldRunRecoveryAt(ZonedDateTime.now())
    }

    internal fun shouldRunAt(now: ZonedDateTime): Boolean {
        return shouldRunOrderSubmissionAt(now)
    }

    internal fun shouldRunOrderSubmissionAt(now: ZonedDateTime): Boolean {
        val tradingHours = properties.tradingHours
        if (!tradingHours.enabled) return true

        val overseasUs = tradingHours.overseasUs
        if (!overseasUs.enabled) return true

        val zone = overseasUs.zoneId.toZoneIdOrNull() ?: return false
        val localDateTime = now.withZoneSameInstant(zone)
        val localDate = localDateTime.toLocalDate()
        if (!overseasUs.isTradingDate(localDate)) return false

        val open = overseasUs.regularOpen.toLocalTimeOrNull() ?: return false
        val regularClose = overseasUs.regularClose.toLocalTimeOrNull() ?: return false
        val close = overseasUs.effectiveClose(localDate, regularClose) ?: return false
        if (!close.isAfter(open)) return false

        val localTime = localDateTime.toLocalTime()
        return !localTime.isBefore(open) && localTime.isBefore(close)
    }

    internal fun shouldRunRecoveryAt(now: ZonedDateTime): Boolean {
        val tradingHours = properties.tradingHours
        if (!tradingHours.enabled) return true

        val overseasUs = tradingHours.overseasUs
        if (!overseasUs.enabled) return true

        val zone = overseasUs.zoneId.toZoneIdOrNull() ?: return false
        val localDate = now.withZoneSameInstant(zone).toLocalDate()
        return overseasUs.isTradingDate(localDate)
    }

    private fun OrderRiskProperties.MarketTradingHours.isTradingDate(localDate: LocalDate): Boolean {
        if (weekdaysOnly && localDate.dayOfWeek in CLOSED_WEEKDAYS) return false
        if (isConfiguredHoliday(localDate)) return false
        if (defaultUsEquityCalendarEnabled && UsEquityMarketCalendar.isMarketHoliday(localDate)) return false
        return true
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

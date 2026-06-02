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
        if (overseasUs.weekdaysOnly && localDate.dayOfWeek in CLOSED_WEEKDAYS) return false
        if (overseasUs.isConfiguredHoliday(localDate)) return false
        if (overseasUs.defaultUsEquityCalendarEnabled && UsEquityMarketCalendar.isMarketHoliday(localDate)) {
            return false
        }

        return true
    }

    private fun OrderRiskProperties.MarketTradingHours.isConfiguredHoliday(date: LocalDate): Boolean {
        return date in holidays.mapNotNull { it.toLocalDateOrNull() }.toSet()
    }

    private fun String.toZoneIdOrNull(): ZoneId? {
        return runCatching { ZoneId.of(trim()) }.getOrNull()
    }

    private fun String.toLocalDateOrNull(): LocalDate? {
        return runCatching { LocalDate.parse(trim()) }.getOrNull()
    }

    companion object {
        private val CLOSED_WEEKDAYS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

package com.example.stocksearchservice.adapter.`in`.scheduler

import com.example.common.market.UsEquityMarketCalendar
import com.example.stocksearchservice.application.port.`in`.RequestStrategyDiscoveryRunCommand
import com.example.stocksearchservice.application.port.`in`.RequestStrategyDiscoveryRunUseCase
import com.example.stocksearchservice.application.port.`in`.StrategyDiscoveryType
import java.time.ZoneId
import java.time.ZonedDateTime
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
internal class StrategyDiscoveryScheduler(
    private val requestStrategyDiscoveryRunUseCase: RequestStrategyDiscoveryRunUseCase,
    private val scheduleGate: StrategyDiscoveryScheduleGate,
) {

    @Retryable(
        value = [Exception::class],
        maxAttempts = 5,
        backoff = Backoff(delay = 1000),
    )
    @Scheduled(cron = EVERY_MINUTE_CRON)
    suspend fun requestStrategyDiscoveryRun() {
        if (!scheduleGate.shouldRequestDiscoveryNow()) return

        requestStrategyDiscoveryRunUseCase.execute(
            RequestStrategyDiscoveryRunCommand(
                strategyType = StrategyDiscoveryType.FINAL_PRICE_BATING_V1,
                requestedAt = ZonedDateTime.now(),
            ),
        )
    }

    companion object {
        private const val EVERY_MINUTE_CRON = "0 */1 * * * *"
    }
}

internal fun interface StrategyDiscoveryScheduleGate {
    fun shouldRequestDiscoveryNow(): Boolean
}

@Component
internal class UsEquityStrategyDiscoveryScheduleGate : StrategyDiscoveryScheduleGate {
    override fun shouldRequestDiscoveryNow(): Boolean {
        return shouldRequestDiscoveryAt(ZonedDateTime.now(US_MARKET_ZONE))
    }

    internal fun shouldRequestDiscoveryAt(now: ZonedDateTime): Boolean {
        val marketDate = now.withZoneSameInstant(US_MARKET_ZONE).toLocalDate()
        return UsEquityMarketCalendar.isTradingDay(marketDate)
    }

    companion object {
        private val US_MARKET_ZONE: ZoneId = ZoneId.of("America/New_York")
    }
}

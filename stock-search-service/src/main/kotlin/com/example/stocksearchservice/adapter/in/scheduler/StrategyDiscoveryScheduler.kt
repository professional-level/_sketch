package com.example.stocksearchservice.adapter.`in`.scheduler

import com.example.stocksearchservice.application.port.`in`.RequestStrategyDiscoveryRunCommand
import com.example.stocksearchservice.application.port.`in`.RequestStrategyDiscoveryRunUseCase
import com.example.stocksearchservice.application.port.`in`.StrategyDiscoveryType
import java.time.ZonedDateTime
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
internal class StrategyDiscoveryScheduler(
    private val requestStrategyDiscoveryRunUseCase: RequestStrategyDiscoveryRunUseCase,
) {

    @Retryable(
        value = [Exception::class],
        maxAttempts = 5,
        backoff = Backoff(delay = 1000),
    )
    @Scheduled(cron = "0 */1 * ? * MON-FRI")
    suspend fun requestStrategyDiscoveryRun() {
        requestStrategyDiscoveryRunUseCase.execute(
            RequestStrategyDiscoveryRunCommand(
                strategyType = StrategyDiscoveryType.FINAL_PRICE_BATING_V1,
                requestedAt = ZonedDateTime.now(),
            ),
        )
    }
}

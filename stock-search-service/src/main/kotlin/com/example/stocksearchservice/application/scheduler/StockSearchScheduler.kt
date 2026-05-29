package com.example.stocksearchservice.application.scheduler

import com.example.stocksearchservice.application.port.`in`.DiscoverFinalPriceBatingCandidatesUseCase
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
internal class StockSearchScheduler(
    private val discoverFinalPriceBatingCandidatesUseCase: DiscoverFinalPriceBatingCandidatesUseCase,
) {
    // TODO: 해당 알고리즘을 관리하는것을 어떻게 해야 할지 고민이 필요.
//    @Scheduled(cron = "0 50 15 ? * MON-FRI")
    // TODO: retry 동작 안함
    @Retryable( // TODO: 실패를 고려해 임의로 retryable 처리.
        value = [Exception::class],
        maxAttempts = 5,
        backoff = Backoff(delay = 1000)
    )
    @Scheduled(cron = "0 */1 * ? * MON-FRI")
    suspend fun finalPriceBatingStrategy1() {
        discoverFinalPriceBatingCandidatesUseCase.execute()
    }
}

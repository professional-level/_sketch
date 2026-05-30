package com.example.stocksearchservice.application.service

import com.example.stocksearchservice.application.port.`in`.DiscoverFinalPriceBatingCandidatesResult
import com.example.stocksearchservice.application.port.`in`.DiscoverFinalPriceBatingCandidatesUseCase
import com.example.stocksearchservice.application.port.`in`.RequestStrategyDiscoveryRunCommand
import com.example.stocksearchservice.application.port.`in`.StrategyDiscoveryType
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestStrategyDiscoveryRunServiceTest {

    @Test
    fun `delegates final price bating discovery through generic strategy discovery request`() = runBlocking {
        val requestedAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00")
        val discoverUseCase = FakeDiscoverFinalPriceBatingCandidatesUseCase(savedCount = 3)
        val service = RequestStrategyDiscoveryRunService(discoverUseCase)

        val result = service.execute(
            RequestStrategyDiscoveryRunCommand(
                strategyType = StrategyDiscoveryType.FINAL_PRICE_BATING_V1,
                requestedAt = requestedAt,
            ),
        )

        assertEquals(1, discoverUseCase.callCount)
        assertEquals(StrategyDiscoveryType.FINAL_PRICE_BATING_V1, result.strategyType)
        assertEquals(requestedAt, result.requestedAt)
        assertEquals(3, result.discoveredCount)
    }

    private class FakeDiscoverFinalPriceBatingCandidatesUseCase(
        private val savedCount: Int,
    ) : DiscoverFinalPriceBatingCandidatesUseCase {
        var callCount: Int = 0

        override suspend fun execute(): DiscoverFinalPriceBatingCandidatesResult {
            callCount += 1
            return DiscoverFinalPriceBatingCandidatesResult(savedCount = savedCount)
        }
    }
}

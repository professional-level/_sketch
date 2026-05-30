package com.example.stocksearchservice.application.service

import com.example.common.UseCaseImpl
import com.example.stocksearchservice.application.port.`in`.DiscoverFinalPriceBatingCandidatesUseCase
import com.example.stocksearchservice.application.port.`in`.RequestStrategyDiscoveryRunCommand
import com.example.stocksearchservice.application.port.`in`.RequestStrategyDiscoveryRunUseCase
import com.example.stocksearchservice.application.port.`in`.StrategyDiscoveryRunResult
import com.example.stocksearchservice.application.port.`in`.StrategyDiscoveryType

@UseCaseImpl
class RequestStrategyDiscoveryRunService(
    private val discoverFinalPriceBatingCandidatesUseCase: DiscoverFinalPriceBatingCandidatesUseCase,
) : RequestStrategyDiscoveryRunUseCase {

    override suspend fun execute(command: RequestStrategyDiscoveryRunCommand): StrategyDiscoveryRunResult {
        val discoveredCount = when (command.strategyType) {
            StrategyDiscoveryType.FINAL_PRICE_BATING_V1 ->
                discoverFinalPriceBatingCandidatesUseCase.execute().savedCount
        }

        return StrategyDiscoveryRunResult(
            strategyType = command.strategyType,
            requestedAt = command.requestedAt,
            discoveredCount = discoveredCount,
        )
    }
}

package com.example.stocksearchservice.application.port.`in`

import com.example.common.UseCase
import java.time.ZonedDateTime

@UseCase
interface RequestStrategyDiscoveryRunUseCase {
    suspend fun execute(command: RequestStrategyDiscoveryRunCommand): StrategyDiscoveryRunResult
}

data class RequestStrategyDiscoveryRunCommand(
    val strategyType: StrategyDiscoveryType,
    val requestedAt: ZonedDateTime,
)

data class StrategyDiscoveryRunResult(
    val strategyType: StrategyDiscoveryType,
    val requestedAt: ZonedDateTime,
    val discoveredCount: Int,
)

enum class StrategyDiscoveryType {
    FINAL_PRICE_BATING_V1,
}

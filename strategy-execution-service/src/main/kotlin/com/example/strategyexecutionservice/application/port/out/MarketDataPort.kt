package com.example.strategyexecutionservice.application.port.out

import java.time.LocalDate

interface MarketDataPort {
    suspend fun getMarketSnapshot(symbol: String, asOfDate: LocalDate, recentCloseCount: Int): StrategyMarketDataSnapshot
}

data class StrategyMarketDataSnapshot(
    val previousClose: Double,
    val recentClosePrices: List<Double> = emptyList(),
) {
    init {
        require(previousClose > 0.0) { "previousClose must be positive" }
        require(recentClosePrices.all { it > 0.0 }) { "recentClosePrices must be positive" }
    }
}

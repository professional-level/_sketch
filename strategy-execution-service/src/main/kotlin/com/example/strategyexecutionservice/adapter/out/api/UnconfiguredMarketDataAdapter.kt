package com.example.strategyexecutionservice.adapter.out.api

import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.out.MarketDataPort
import com.example.strategyexecutionservice.application.port.out.StrategyMarketDataSnapshot

@ExternalApiAdapter
internal class UnconfiguredMarketDataAdapter : MarketDataPort {
    override suspend fun getMarketSnapshot(
        symbol: String,
        recentCloseCount: Int,
    ): StrategyMarketDataSnapshot {
        throw UnsupportedOperationException(
            "MarketDataPort is not configured for symbol=$symbol, recentCloseCount=$recentCloseCount",
        )
    }
}

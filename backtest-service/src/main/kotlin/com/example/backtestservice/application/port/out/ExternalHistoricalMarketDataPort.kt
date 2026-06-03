package com.example.backtestservice.application.port.out

import com.example.backtestservice.domain.market.HistoricalCandle
import java.time.LocalDate

interface ExternalHistoricalMarketDataPort {
    fun fetchDailyCandles(query: ExternalHistoricalDailyCandlesQuery): List<HistoricalCandle>
}

data class ExternalHistoricalDailyCandlesQuery(
    val symbol: String,
    val market: String = "US",
    val from: LocalDate,
    val to: LocalDate,
    val autoAdjust: Boolean = false,
    val timeoutSeconds: Long = 30,
)

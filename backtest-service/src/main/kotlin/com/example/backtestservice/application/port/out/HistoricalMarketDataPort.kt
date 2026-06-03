package com.example.backtestservice.application.port.out

import com.example.backtestservice.domain.market.HistoricalCandle
import java.time.LocalDate

interface HistoricalMarketDataPort {
    fun findDailyCandles(query: HistoricalDailyCandlesQuery): List<HistoricalCandle>
}

data class HistoricalDailyCandlesQuery(
    val symbol: String,
    val market: String = "US",
    val from: LocalDate,
    val to: LocalDate,
)

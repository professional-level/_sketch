package com.example.backtestservice.application.port.out

import java.time.LocalDate

interface MarketCalendarPort {
    fun findTradingDays(query: MarketTradingDaysQuery): MarketTradingDaysResult

    fun isTradingDay(query: MarketTradingDayQuery): MarketTradingDayResult {
        val result = findTradingDays(
            MarketTradingDaysQuery(
                market = query.market,
                from = query.date,
                to = query.date,
            ),
        )
        return MarketTradingDayResult(
            market = result.market,
            date = query.date,
            tradingDay = query.date in result.dates,
            source = result.source,
        )
    }
}

data class MarketTradingDayQuery(
    val market: String = "US",
    val date: LocalDate,
)

data class MarketTradingDayResult(
    val market: String,
    val date: LocalDate,
    val tradingDay: Boolean,
    val source: MarketCalendarSource,
)

data class MarketTradingDaysQuery(
    val market: String = "US",
    val from: LocalDate,
    val to: LocalDate,
)

data class MarketTradingDaysResult(
    val market: String,
    val dates: List<LocalDate>,
    val source: MarketCalendarSource,
)

enum class MarketCalendarSource {
    PANDAS_MARKET_CALENDARS,
    US_EQUITY_MARKET_CALENDAR_FALLBACK,
}

package com.example.strategyexecutionservice.application.port.out

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

interface TradingCalendarPort {
    fun isTradingDay(market: TradingMarket, date: LocalDate): Boolean

    fun tradingDate(market: TradingMarket, requestedAt: ZonedDateTime): LocalDate {
        return requestedAt.toLocalDate()
    }

    fun orderSessionDate(market: TradingMarket, requestedAt: ZonedDateTime): LocalDate {
        return tradingDate(market, requestedAt)
    }

    fun earlyCloseTime(market: TradingMarket, date: LocalDate): LocalTime? {
        return null
    }
}

enum class TradingMarket {
    US,
}

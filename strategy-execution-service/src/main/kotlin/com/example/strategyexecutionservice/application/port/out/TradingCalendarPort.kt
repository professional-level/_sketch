package com.example.strategyexecutionservice.application.port.out

import java.time.LocalDate

interface TradingCalendarPort {
    fun isTradingDay(market: TradingMarket, date: LocalDate): Boolean
}

enum class TradingMarket {
    US,
}

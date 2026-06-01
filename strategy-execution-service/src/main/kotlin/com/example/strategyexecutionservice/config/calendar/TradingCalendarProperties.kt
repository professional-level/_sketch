package com.example.strategyexecutionservice.config.calendar

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "akra.trading-calendar")
class TradingCalendarProperties {
    var us: MarketCalendar = MarketCalendar()

    class MarketCalendar {
        var enabled: Boolean = true
        var weekdaysOnly: Boolean = true
        var holidays: List<String> = emptyList()
        var earlyCloseDays: List<String> = emptyList()
    }
}

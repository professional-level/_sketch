package com.example.strategyexecutionservice.config.calendar

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "akra.trading-calendar")
class TradingCalendarProperties {
    var us: MarketCalendar = MarketCalendar()

    class MarketCalendar {
        var enabled: Boolean = true
        var zoneId: String = "America/New_York"
        var weekdaysOnly: Boolean = true
        var defaultUsEquityCalendarEnabled: Boolean = true
        var holidays: List<String> = emptyList()
        var earlyCloseDays: List<String> = emptyList()
        var earlyCloseTime: String? = null
        var earlyCloseTimes: MutableMap<String, String> = mutableMapOf()
    }
}

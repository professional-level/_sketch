package com.example.stockpurchaseservice.config.risk

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "akra.order.risk")
class OrderRiskProperties {
    var enabled: Boolean = true
    var maxOrderNotional: Double? = null
    var maxAccountPendingBuyNotional: Double? = null
    var maxDailyOrderCount: Long? = null
    var duplicateOrderKillSwitchEnabled: Boolean = true
    var disabledStrategyPrefixes: List<String> = emptyList()
    var symbolMaxOrderNotional: MutableMap<String, Double> = mutableMapOf()
    var tradingHours: TradingHoursProperties = TradingHoursProperties()

    class TradingHoursProperties {
        var enabled: Boolean = true
        var domestic: MarketTradingHours = MarketTradingHours().apply {
            zoneId = "Asia/Seoul"
            regularOpen = "09:00"
            regularClose = "15:30"
            locCutoff = "15:30"
            mocCutoff = "15:30"
        }
        var overseasUs: MarketTradingHours = MarketTradingHours().apply {
            zoneId = "America/New_York"
            regularOpen = "09:30"
            regularClose = "16:00"
            locCutoff = "15:50"
            mocCutoff = "15:50"
            earlyCloseTime = "13:00"
        }
    }

    class MarketTradingHours {
        var enabled: Boolean = true
        var zoneId: String = "UTC"
        var weekdaysOnly: Boolean = true
        var holidays: List<String> = emptyList()
        var earlyCloseDates: List<String> = emptyList()
        var earlyCloseTime: String? = null
        var regularOpen: String = "00:00"
        var regularClose: String = "23:59"
        var locCutoff: String? = null
        var mocCutoff: String? = null
    }
}

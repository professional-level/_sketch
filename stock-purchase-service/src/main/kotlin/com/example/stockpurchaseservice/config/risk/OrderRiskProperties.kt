package com.example.stockpurchaseservice.config.risk

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "akra.order.risk")
class OrderRiskProperties {
    var enabled: Boolean = true
    var maxOrderNotional: Double? = null
    var maxDailyOrderCount: Long? = null
    var duplicateOrderKillSwitchEnabled: Boolean = true
    var disabledStrategyPrefixes: List<String> = emptyList()
    var symbolMaxOrderNotional: MutableMap<String, Double> = mutableMapOf()
}

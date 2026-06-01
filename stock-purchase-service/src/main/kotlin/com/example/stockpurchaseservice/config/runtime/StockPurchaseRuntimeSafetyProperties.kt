package com.example.stockpurchaseservice.config.runtime

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "akra.runtime.safety")
class StockPurchaseRuntimeSafetyProperties {
    var enabled: Boolean = true
    var productionProfiles: List<String> = listOf("prod", "production", "live")
    var allowLocalBrokerEndpointInProduction: Boolean = false
    var allowMockTradingInProduction: Boolean = false
}

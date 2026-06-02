package com.example.stocksearchservice.config.runtime

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "akra.runtime.safety")
class StockSearchRuntimeSafetyProperties {
    var enabled: Boolean = true
    var productionProfiles: List<String> = listOf("prod", "production", "live")
    var allowLocalTemporalTargetInProduction: Boolean = false
    var allowApplicationSecretPropertySourceInProduction: Boolean = false
}

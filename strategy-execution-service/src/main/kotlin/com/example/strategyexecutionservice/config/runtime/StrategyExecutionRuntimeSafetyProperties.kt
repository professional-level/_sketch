package com.example.strategyexecutionservice.config.runtime

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "akra.runtime.safety")
class StrategyExecutionRuntimeSafetyProperties {
    var enabled: Boolean = true
    var productionProfiles: List<String> = listOf("prod", "production", "live")
    var allowLocalTemporalTargetInProduction: Boolean = false
    var allowLocalMarketDataEndpointInProduction: Boolean = false
}

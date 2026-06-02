package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.out.OrderTradingEnvironment
import com.example.strategyexecutionservice.config.orderintent.OrderIntentProperties
import org.springframework.stereotype.Component

@Component
class OrderIntentTradingEnvironmentResolver(
    private val properties: OrderIntentProperties = OrderIntentProperties(),
) {
    fun resolve(strategyExecutionId: String): OrderTradingEnvironment {
        return properties.strategyTradingEnvironments
            .mapNotNull { (prefix, environment) ->
                val normalizedPrefix = prefix.trim()
                if (normalizedPrefix.isBlank() || !strategyExecutionId.startsWith(normalizedPrefix)) {
                    null
                } else {
                    normalizedPrefix to environment
                }
            }
            .maxByOrNull { it.first.length }
            ?.second
            ?: properties.defaultTradingEnvironment
    }
}

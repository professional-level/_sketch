package com.example.stockpurchaseservice.config.runtime

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class StockPurchaseRuntimeSafetyValidator(
    private val environment: Environment,
    private val properties: StockPurchaseRuntimeSafetyProperties,
    @Value("\${akra.order.kis-open-api.base-url:http://localhost:8079}")
    private val brokerBaseUrl: String,
    @Value("\${akra.order.domestic.mock:true}")
    private val domesticMockOrder: Boolean,
    @Value("\${akra.order.overseas.mock:true}")
    private val overseasMockOrder: Boolean,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            StockPurchaseRuntimeSafetyRules.Input(
                activeProfiles = environment.activeProfiles.toList(),
                enabled = properties.enabled,
                productionProfiles = properties.productionProfiles,
                brokerBaseUrl = brokerBaseUrl,
                domesticMockOrder = domesticMockOrder,
                overseasMockOrder = overseasMockOrder,
                allowLocalBrokerEndpointInProduction = properties.allowLocalBrokerEndpointInProduction,
                allowMockTradingInProduction = properties.allowMockTradingInProduction,
            ),
        )

        if (violations.isNotEmpty()) {
            throw IllegalStateException(
                violations.joinToString(
                    separator = System.lineSeparator() + "- ",
                    prefix = "Unsafe stock-purchase-service runtime configuration:" +
                        System.lineSeparator() + "- ",
                ),
            )
        }
    }
}

object StockPurchaseRuntimeSafetyRules {
    data class Input(
        val activeProfiles: List<String>,
        val enabled: Boolean,
        val productionProfiles: List<String>,
        val brokerBaseUrl: String,
        val domesticMockOrder: Boolean,
        val overseasMockOrder: Boolean,
        val allowLocalBrokerEndpointInProduction: Boolean,
        val allowMockTradingInProduction: Boolean,
    )

    fun validate(input: Input): List<String> {
        if (!input.enabled || !isProduction(input.activeProfiles, input.productionProfiles)) {
            return emptyList()
        }

        val violations = mutableListOf<String>()
        if (!input.allowLocalBrokerEndpointInProduction && isLocalEndpoint(input.brokerBaseUrl)) {
            violations += "prod/live profile cannot use a local KIS wrapper endpoint " +
                "(akra.order.kis-open-api.base-url=${input.brokerBaseUrl})"
        }

        if (!input.allowMockTradingInProduction && (input.domesticMockOrder || input.overseasMockOrder)) {
            violations += "prod/live profile cannot enable mock order flags unless " +
                "akra.runtime.safety.allow-mock-trading-in-production=true"
        }

        return violations
    }

    private fun isProduction(activeProfiles: List<String>, productionProfiles: List<String>): Boolean {
        val productionProfileSet = productionProfiles.map { it.trim().lowercase() }.toSet()
        return activeProfiles
            .map { it.trim().lowercase() }
            .any { it in productionProfileSet }
    }

    private fun isLocalEndpoint(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.contains("localhost") ||
            normalized.contains("127.0.0.1") ||
            normalized.contains("0.0.0.0") ||
            normalized.contains("[::1]") ||
            normalized == "::1" ||
            normalized.startsWith("::1:")
    }
}

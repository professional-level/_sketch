package com.example.strategyexecutionservice.config.runtime

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class StrategyExecutionRuntimeSafetyValidator(
    private val environment: Environment,
    private val properties: StrategyExecutionRuntimeSafetyProperties,
    @Value("\${akra.temporal.enabled:false}")
    private val temporalEnabled: Boolean,
    @Value("\${akra.temporal.target:127.0.0.1:7233}")
    private val temporalTarget: String,
    @Value("\${akra.market-data.kis-open-api.base-url:http://localhost:8079}")
    private val marketDataBaseUrl: String,
    @Value("\${akra.order-intent.default-trading-environment:MOCK}")
    private val defaultOrderIntentTradingEnvironment: String,
    @Value("\${akra.trading-calendar.us.enabled:true}")
    private val usTradingCalendarEnabled: Boolean,
    @Value("\${akra.trading-calendar.us.default-us-equity-calendar-enabled:true}")
    private val defaultUsEquityCalendarEnabled: Boolean,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            StrategyExecutionRuntimeSafetyRules.Input(
                activeProfiles = environment.activeProfiles.toList(),
                enabled = properties.enabled,
                productionProfiles = properties.productionProfiles,
                temporalEnabled = temporalEnabled,
                temporalTarget = temporalTarget,
                marketDataBaseUrl = marketDataBaseUrl,
                hibernateDdlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto"),
                defaultOrderIntentTradingEnvironment = defaultOrderIntentTradingEnvironment,
                usTradingCalendarEnabled = usTradingCalendarEnabled,
                defaultUsEquityCalendarEnabled = defaultUsEquityCalendarEnabled,
                allowLocalTemporalTargetInProduction = properties.allowLocalTemporalTargetInProduction,
                allowLocalMarketDataEndpointInProduction = properties.allowLocalMarketDataEndpointInProduction,
                allowMockOrderIntentInProduction = properties.allowMockOrderIntentInProduction,
                allowDisabledTradingCalendarInProduction = properties.allowDisabledTradingCalendarInProduction,
            ),
        )

        if (violations.isNotEmpty()) {
            throw IllegalStateException(
                violations.joinToString(
                    separator = System.lineSeparator() + "- ",
                    prefix = "Unsafe strategy-execution-service runtime configuration:" +
                        System.lineSeparator() + "- ",
                ),
            )
        }
    }
}

object StrategyExecutionRuntimeSafetyRules {
    data class Input(
        val activeProfiles: List<String>,
        val enabled: Boolean,
        val productionProfiles: List<String>,
        val temporalEnabled: Boolean,
        val temporalTarget: String,
        val marketDataBaseUrl: String,
        val hibernateDdlAuto: String?,
        val defaultOrderIntentTradingEnvironment: String,
        val usTradingCalendarEnabled: Boolean,
        val defaultUsEquityCalendarEnabled: Boolean,
        val allowLocalTemporalTargetInProduction: Boolean,
        val allowLocalMarketDataEndpointInProduction: Boolean,
        val allowMockOrderIntentInProduction: Boolean,
        val allowDisabledTradingCalendarInProduction: Boolean,
    )

    fun validate(input: Input): List<String> {
        if (!input.enabled || !isProduction(input.activeProfiles, input.productionProfiles)) {
            return emptyList()
        }

        val violations = mutableListOf<String>()
        if (input.temporalEnabled && input.temporalTarget.isBlank()) {
            violations += "prod/live profile must configure a non-blank Temporal target " +
                "(akra.temporal.target)"
        }
        if (
            input.temporalEnabled &&
            !input.allowLocalTemporalTargetInProduction &&
            isLocalEndpoint(input.temporalTarget)
        ) {
            violations += "prod/live profile cannot use a local Temporal target " +
                "(akra.temporal.target=${input.temporalTarget})"
        }

        if (input.marketDataBaseUrl.isBlank()) {
            violations += "prod/live profile must configure a non-blank market-data KIS wrapper endpoint " +
                "(akra.market-data.kis-open-api.base-url)"
        }
        if (!input.allowLocalMarketDataEndpointInProduction && isLocalEndpoint(input.marketDataBaseUrl)) {
            violations += "prod/live profile cannot use a local market-data KIS wrapper endpoint " +
                "(akra.market-data.kis-open-api.base-url=${input.marketDataBaseUrl})"
        }

        if (!isSafeHibernateDdlAuto(input.hibernateDdlAuto)) {
            violations += "prod/live profile cannot use Hibernate automatic DDL " +
                "(spring.jpa.hibernate.ddl-auto=${input.hibernateDdlAuto}); apply migrations explicitly and use none or validate"
        }

        if (
            !input.allowMockOrderIntentInProduction &&
            input.defaultOrderIntentTradingEnvironment.trim().uppercase() != "LIVE"
        ) {
            violations += "prod/live profile must default order intents to LIVE " +
                "(akra.order-intent.default-trading-environment=${input.defaultOrderIntentTradingEnvironment})"
        }

        if (!input.allowDisabledTradingCalendarInProduction) {
            if (!input.usTradingCalendarEnabled) {
                violations += "prod/live profile cannot disable US trading calendar " +
                    "(akra.trading-calendar.us.enabled=false)"
            } else if (!input.defaultUsEquityCalendarEnabled) {
                violations += "prod/live profile cannot disable default US equity calendar " +
                    "(akra.trading-calendar.us.default-us-equity-calendar-enabled=false)"
            }
        }

        return violations
    }

    private fun isSafeHibernateDdlAuto(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return normalized.isEmpty() || normalized == "none" || normalized == "validate"
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
            normalized.contains("host.docker.internal") ||
            normalized.contains("127.0.0.1") ||
            normalized.contains("0.0.0.0") ||
            normalized.contains("[::1]") ||
            normalized == "::1" ||
            normalized.startsWith("::1:")
    }
}

package com.example.stockpurchaseservice.config.runtime

import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class StockPurchaseRuntimeSafetyValidator(
    private val environment: Environment,
    private val properties: StockPurchaseRuntimeSafetyProperties,
    private val orderRiskProperties: OrderRiskProperties,
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
                hibernateDdlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto"),
                orderRiskEnabled = orderRiskProperties.enabled,
                sellPositionRiskEnabled = orderRiskProperties.sellPosition.enabled,
                tradingHoursRiskEnabled = orderRiskProperties.tradingHours.enabled,
                duplicateOrderKillSwitchEnabled = orderRiskProperties.duplicateOrderKillSwitchEnabled,
                accountCashRiskEnabled = orderRiskProperties.accountCash.enabled,
                maxOrderNotional = orderRiskProperties.maxOrderNotional,
                maxAccountPendingBuyNotional = orderRiskProperties.maxAccountPendingBuyNotional,
                maxAccountExposureNotional = orderRiskProperties.maxAccountExposureNotional,
                maxDailyOrderCount = orderRiskProperties.maxDailyOrderCount,
                enabledStrategyPrefixes = orderRiskProperties.enabledStrategyPrefixes,
                symbolMaxOrderNotional = orderRiskProperties.symbolMaxOrderNotional,
                strategyTradingEnvironmentPrefixes = orderRiskProperties.strategyTradingEnvironments.keys,
                allowLocalBrokerEndpointInProduction = properties.allowLocalBrokerEndpointInProduction,
                allowMockTradingInProduction = properties.allowMockTradingInProduction,
                allowDisabledRiskControlsInProduction = properties.allowDisabledRiskControlsInProduction,
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
        val hibernateDdlAuto: String?,
        val orderRiskEnabled: Boolean,
        val sellPositionRiskEnabled: Boolean,
        val tradingHoursRiskEnabled: Boolean,
        val duplicateOrderKillSwitchEnabled: Boolean,
        val accountCashRiskEnabled: Boolean,
        val maxOrderNotional: Double?,
        val maxAccountPendingBuyNotional: Double?,
        val maxAccountExposureNotional: Double?,
        val maxDailyOrderCount: Long?,
        val enabledStrategyPrefixes: Collection<String>,
        val symbolMaxOrderNotional: Map<String, Double>,
        val strategyTradingEnvironmentPrefixes: Collection<String>,
        val allowLocalBrokerEndpointInProduction: Boolean,
        val allowMockTradingInProduction: Boolean,
        val allowDisabledRiskControlsInProduction: Boolean,
    )

    fun validate(input: Input): List<String> {
        if (!input.enabled || !isProduction(input.activeProfiles, input.productionProfiles)) {
            return emptyList()
        }

        val violations = mutableListOf<String>()
        if (input.brokerBaseUrl.isBlank()) {
            violations += "prod/live profile must configure a non-blank KIS wrapper endpoint " +
                "(akra.order.kis-open-api.base-url)"
        }
        if (!input.allowLocalBrokerEndpointInProduction && isLocalEndpoint(input.brokerBaseUrl)) {
            violations += "prod/live profile cannot use a local KIS wrapper endpoint " +
                "(akra.order.kis-open-api.base-url=${input.brokerBaseUrl})"
        }

        if (!input.allowMockTradingInProduction && (input.domesticMockOrder || input.overseasMockOrder)) {
            violations += "prod/live profile cannot enable mock order flags unless " +
                "akra.runtime.safety.allow-mock-trading-in-production=true"
        }

        if (!isSafeHibernateDdlAuto(input.hibernateDdlAuto)) {
            violations += "prod/live profile cannot use Hibernate automatic DDL " +
                "(spring.jpa.hibernate.ddl-auto=${input.hibernateDdlAuto}); apply migrations explicitly and use none or validate"
        }

        if (!input.allowDisabledRiskControlsInProduction) {
            if (!input.orderRiskEnabled) {
                violations += "prod/live profile cannot disable order risk controls " +
                    "(akra.order.risk.enabled=false)"
            } else {
                if (!input.sellPositionRiskEnabled) {
                    violations += "prod/live profile cannot disable sell position risk control " +
                        "(akra.order.risk.sell-position.enabled=false)"
                }
                if (!input.tradingHoursRiskEnabled) {
                    violations += "prod/live profile cannot disable trading-hours risk control " +
                        "(akra.order.risk.trading-hours.enabled=false)"
                }
                if (!input.duplicateOrderKillSwitchEnabled) {
                    violations += "prod/live profile cannot disable duplicate active-order kill switch " +
                        "(akra.order.risk.duplicate-order-kill-switch-enabled=false)"
                }
                if (!input.accountCashRiskEnabled) {
                    violations += "prod/live profile cannot disable account cash risk control " +
                        "(akra.order.risk.account-cash.enabled=false)"
                }
                if (!input.maxOrderNotional.isPositiveLimit()) {
                    violations += "prod/live profile must configure a positive order notional limit " +
                        "(akra.order.risk.max-order-notional)"
                }
                if (!input.maxAccountPendingBuyNotional.isPositiveLimit()) {
                    violations += "prod/live profile must configure a positive account pending-buy notional limit " +
                        "(akra.order.risk.max-account-pending-buy-notional)"
                }
                if (!input.maxAccountExposureNotional.isPositiveLimit()) {
                    violations += "prod/live profile must configure a positive account exposure notional limit " +
                        "(akra.order.risk.max-account-exposure-notional)"
                }
                if (!input.maxDailyOrderCount.isPositiveLimit()) {
                    violations += "prod/live profile must configure a positive daily order count limit " +
                        "(akra.order.risk.max-daily-order-count)"
                }
                if (!input.enabledStrategyPrefixes.hasConfiguredValue()) {
                    violations += "prod/live profile must configure a strategy allow-list " +
                        "(akra.order.risk.enabled-strategy-prefixes)"
                }
                if (!input.symbolMaxOrderNotional.hasPositiveSymbolLimit()) {
                    violations += "prod/live profile must configure at least one positive symbol order notional limit " +
                        "(akra.order.risk.symbol-max-order-notional.*)"
                }
                if (!input.strategyTradingEnvironmentPrefixes.hasConfiguredValue()) {
                    violations += "prod/live profile must configure strategy trading environment policies " +
                        "(akra.order.risk.strategy-trading-environments)"
                }
            }
        }

        return violations
    }

    private fun Double?.isPositiveLimit(): Boolean {
        return this != null && isFinite() && this > 0.0
    }

    private fun Long?.isPositiveLimit(): Boolean {
        return this != null && this > 0
    }

    private fun Collection<String>.hasConfiguredValue(): Boolean {
        return any { it.trim().isNotBlank() }
    }

    private fun Map<String, Double>.hasPositiveSymbolLimit(): Boolean {
        return entries.any { (symbol, limit) -> symbol.trim().isNotBlank() && limit.isFinite() && limit > 0.0 }
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

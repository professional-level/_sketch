package com.example.stockpurchaseservice.config.runtime

import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
                currencyConversionProvider = orderRiskProperties.currencyConversion.provider,
                riskBaseCurrency = orderRiskProperties.currencyConversion.baseCurrency,
                domesticCurrency = orderRiskProperties.currencyConversion.domesticCurrency,
                overseasUsCurrency = orderRiskProperties.currencyConversion.overseasUsCurrency,
                staticRatesToBase = orderRiskProperties.currencyConversion.ratesToBase,
                httpFxRateBaseUrl = orderRiskProperties.currencyConversion.http.baseUrl,
                tradingHoursWindows = listOf(
                    orderRiskProperties.tradingHours.domestic.toRuntimeSafetyInput("DOMESTIC"),
                    orderRiskProperties.tradingHours.overseasUs.toRuntimeSafetyInput("OVERSEAS_US"),
                ),
                kisWrapperFxPairKeysWithSymbol = orderRiskProperties.currencyConversion.kisWrapper.pairs
                    .filterValues { it.symbol.isNotBlank() }
                    .keys,
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

    private fun OrderRiskProperties.MarketTradingHours.toRuntimeSafetyInput(
        market: String,
    ): StockPurchaseRuntimeSafetyRules.TradingHoursWindowInput {
        return StockPurchaseRuntimeSafetyRules.TradingHoursWindowInput(
            market = market,
            enabled = enabled,
            zoneId = zoneId,
            holidays = holidays,
            earlyCloseDates = earlyCloseDates,
            earlyCloseTime = earlyCloseTime,
            earlyCloseTimes = earlyCloseTimes.toMap(),
            regularOpen = regularOpen,
            regularClose = regularClose,
            locCutoff = locCutoff,
            mocCutoff = mocCutoff,
        )
    }
}

object StockPurchaseRuntimeSafetyRules {
    data class TradingHoursWindowInput(
        val market: String,
        val enabled: Boolean,
        val zoneId: String,
        val holidays: List<String>,
        val earlyCloseDates: List<String>,
        val earlyCloseTime: String?,
        val earlyCloseTimes: Map<String, String>,
        val regularOpen: String,
        val regularClose: String,
        val locCutoff: String?,
        val mocCutoff: String?,
    )

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
        val currencyConversionProvider: String,
        val riskBaseCurrency: String,
        val domesticCurrency: String,
        val overseasUsCurrency: String,
        val staticRatesToBase: Map<String, Double>,
        val httpFxRateBaseUrl: String,
        val tradingHoursWindows: List<TradingHoursWindowInput>,
        val kisWrapperFxPairKeysWithSymbol: Collection<String>,
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
                if (input.tradingHoursRiskEnabled) {
                    violations += validateTradingHours(input.tradingHoursWindows)
                }
                violations += validateCurrencyConversion(input)
            }
        }

        return violations
    }

    private fun validateTradingHours(windows: List<TradingHoursWindowInput>): List<String> {
        val violations = mutableListOf<String>()
        windows.filter { it.enabled }.forEach { window ->
            if (parseZoneId(window.zoneId) == null) {
                violations += "prod/live profile has invalid ${window.market} trading-hours zone " +
                    "(zone-id=${window.zoneId})"
            }

            val open = parseLocalTime(window.regularOpen)
            if (open == null) {
                violations += "prod/live profile has invalid ${window.market} trading-hours regular-open " +
                    "(regular-open=${window.regularOpen})"
            }

            val close = parseLocalTime(window.regularClose)
            if (close == null) {
                violations += "prod/live profile has invalid ${window.market} trading-hours regular-close " +
                    "(regular-close=${window.regularClose})"
            }

            if (open != null && close != null && !open.isBefore(close)) {
                violations += "prod/live profile ${window.market} trading-hours regular-open must be before " +
                    "regular-close (regular-open=${window.regularOpen}, regular-close=${window.regularClose})"
            }

            window.holidays.forEach { value ->
                if (parseLocalDate(value) == null) {
                    violations += "prod/live profile has invalid ${window.market} trading-hours holiday date " +
                        "(holidays[]=$value)"
                }
            }

            window.earlyCloseDates.forEach { value ->
                if (parseLocalDate(value) == null) {
                    violations += "prod/live profile has invalid ${window.market} trading-hours early-close date " +
                        "(early-close-dates[]=$value)"
                }
            }

            val locCutoff = validateOrderCutoff(
                market = window.market,
                orderType = "LOC",
                value = window.locCutoff,
                open = open,
                close = close,
                violations = violations,
            )
            val mocCutoff = validateOrderCutoff(
                market = window.market,
                orderType = "MOC",
                value = window.mocCutoff,
                open = open,
                close = close,
                violations = violations,
            )

            val commonEarlyClose = validateCommonEarlyClose(window, open, close, locCutoff, mocCutoff, violations)
            window.earlyCloseTimes.forEach { (date, time) ->
                if (parseLocalDate(date) == null) {
                    violations += "prod/live profile has invalid ${window.market} trading-hours early-close " +
                        "override date (early-close-times[$date])"
                }

                val earlyClose = parseLocalTime(time)
                if (earlyClose == null) {
                    violations += "prod/live profile has invalid ${window.market} trading-hours early-close " +
                        "override time (early-close-times[$date]=$time)"
                } else {
                    validateEarlyCloseTime(window.market, "early-close-times[$date]", time, earlyClose, open, close, violations)
                    validateEarlyCloseOrderCutoffs(
                        market = window.market,
                        source = "early-close-times[$date]",
                        earlyClose = earlyClose,
                        regularClose = close,
                        open = open,
                        locCutoff = locCutoff,
                        mocCutoff = mocCutoff,
                        violations = violations,
                    )
                }
            }

            if (window.earlyCloseDates.isNotEmpty() && window.earlyCloseTime.isNullOrBlank()) {
                val value = window.earlyCloseTime.orEmpty()
                violations += "prod/live profile must configure ${window.market} trading-hours early-close-time " +
                    "when early-close-dates are configured (early-close-time=$value)"
            }
        }

        return violations
    }

    private fun validateCommonEarlyClose(
        window: TradingHoursWindowInput,
        open: LocalTime?,
        close: LocalTime?,
        locCutoff: LocalTime?,
        mocCutoff: LocalTime?,
        violations: MutableList<String>,
    ): LocalTime? {
        val value = window.earlyCloseTime?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val earlyClose = parseLocalTime(value)
        if (earlyClose == null) {
            violations += "prod/live profile has invalid ${window.market} trading-hours early-close-time " +
                "(early-close-time=$value)"
            return null
        }

        validateEarlyCloseTime(window.market, "early-close-time", value, earlyClose, open, close, violations)
        validateEarlyCloseOrderCutoffs(
            market = window.market,
            source = "early-close-time",
            earlyClose = earlyClose,
            regularClose = close,
            open = open,
            locCutoff = locCutoff,
            mocCutoff = mocCutoff,
            violations = violations,
        )
        return earlyClose
    }

    private fun validateOrderCutoff(
        market: String,
        orderType: String,
        value: String?,
        open: LocalTime?,
        close: LocalTime?,
        violations: MutableList<String>,
    ): LocalTime? {
        val configured = value?.trim()?.takeIf { it.isNotEmpty() } ?: return close
        val cutoff = parseLocalTime(configured)
        if (cutoff == null) {
            violations += "prod/live profile has invalid $market trading-hours $orderType cutoff " +
                "($orderType-cutoff=$configured)"
            return null
        }
        if (open != null && !cutoff.isAfter(open)) {
            violations += "prod/live profile $market trading-hours $orderType cutoff must be after regular-open " +
                "($orderType-cutoff=$configured, regular-open=$open)"
        }
        if (close != null && cutoff.isAfter(close)) {
            violations += "prod/live profile $market trading-hours $orderType cutoff cannot be after regular-close " +
                "($orderType-cutoff=$configured, regular-close=$close)"
        }
        return cutoff
    }

    private fun validateEarlyCloseTime(
        market: String,
        property: String,
        value: String,
        earlyClose: LocalTime,
        open: LocalTime?,
        close: LocalTime?,
        violations: MutableList<String>,
    ) {
        if (open != null && !earlyClose.isAfter(open)) {
            violations += "prod/live profile $market trading-hours early close must be after regular-open " +
                "($property=$value, regular-open=$open)"
        }
        if (close != null && !earlyClose.isBefore(close)) {
            violations += "prod/live profile $market trading-hours early close must be before regular-close " +
                "($property=$value, regular-close=$close)"
        }
    }

    private fun validateEarlyCloseOrderCutoffs(
        market: String,
        source: String,
        earlyClose: LocalTime,
        regularClose: LocalTime?,
        open: LocalTime?,
        locCutoff: LocalTime?,
        mocCutoff: LocalTime?,
        violations: MutableList<String>,
    ) {
        if (regularClose == null || open == null) return

        listOf("LOC" to locCutoff, "MOC" to mocCutoff).forEach { (orderType, cutoff) ->
            if (cutoff == null) return@forEach
            val adjustedCutoff = earlyClose.minus(Duration.between(cutoff, regularClose))
            if (!adjustedCutoff.isAfter(open)) {
                violations += "prod/live profile $market trading-hours $orderType cutoff leaves no early-close " +
                    "order window ($source=$earlyClose, $orderType-cutoff=$cutoff, adjusted-cutoff=$adjustedCutoff, " +
                    "regular-open=$open)"
            }
        }
    }

    private fun validateCurrencyConversion(input: Input): List<String> {
        val violations = mutableListOf<String>()
        val baseCurrency = input.riskBaseCurrency.normalizedCurrency()
        val domesticCurrency = input.domesticCurrency.normalizedCurrency()
        val overseasUsCurrency = input.overseasUsCurrency.normalizedCurrency()

        val hasBlankCurrency = baseCurrency.isBlank() || domesticCurrency.isBlank() || overseasUsCurrency.isBlank()
        if (baseCurrency.isBlank()) {
            violations += "prod/live profile must configure a risk base currency " +
                "(akra.order.risk.currency-conversion.base-currency)"
        }
        if (domesticCurrency.isBlank()) {
            violations += "prod/live profile must configure a domestic risk currency " +
                "(akra.order.risk.currency-conversion.domestic-currency)"
        }
        if (overseasUsCurrency.isBlank()) {
            violations += "prod/live profile must configure an overseas US risk currency " +
                "(akra.order.risk.currency-conversion.overseas-us-currency)"
        }

        val provider = input.currencyConversionProvider.trim().lowercase()
        val unsupportedProvider = provider !in FX_PROVIDERS
        if (unsupportedProvider) {
            violations += "prod/live profile must configure a supported FX provider " +
                "(akra.order.risk.currency-conversion.provider=static|http|kis-wrapper)"
        }
        if (hasBlankCurrency || unsupportedProvider) {
            return violations
        }

        val marketCurrencies = listOf(domesticCurrency, overseasUsCurrency)
            .filter { it.isNotBlank() && it != baseCurrency }
            .distinct()
        when (provider) {
            "static" -> marketCurrencies.forEach { currency ->
                if (!input.staticRatesToBase.hasPositiveRateFor(currency)) {
                    violations += "prod/live profile must configure a positive static FX rate " +
                        "for $currency to $baseCurrency " +
                        "(akra.order.risk.currency-conversion.rates-to-base.$currency)"
                }
            }
            "http" -> if (input.httpFxRateBaseUrl.isBlank()) {
                violations += "prod/live profile must configure an HTTP FX base URL " +
                    "(akra.order.risk.currency-conversion.http.base-url)"
            }
            "kis-wrapper" -> marketCurrencies.forEach { currency ->
                if (!input.kisWrapperFxPairKeysWithSymbol.hasFxPair(currency, baseCurrency)) {
                    violations += "prod/live profile must configure a KIS wrapper FX pair " +
                        "for $currency to $baseCurrency " +
                        "(akra.order.risk.currency-conversion.kis-wrapper.pairs.$currency-$baseCurrency.symbol)"
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

    private fun Map<String, Double>.hasPositiveRateFor(currency: String): Boolean {
        return entries.any { (configuredCurrency, rate) ->
            configuredCurrency.normalizedCurrency() == currency && rate.isFinite() && rate > 0.0
        }
    }

    private fun Collection<String>.hasFxPair(sourceCurrency: String, baseCurrency: String): Boolean {
        val expectedKeys = setOf(
            "$sourceCurrency-$baseCurrency",
            "$sourceCurrency:$baseCurrency",
            "$sourceCurrency/$baseCurrency",
            "$sourceCurrency$baseCurrency",
        )
        return any { it.normalizedFxPairKey() in expectedKeys }
    }

    private fun String.normalizedCurrency(): String {
        return trim().uppercase()
    }

    private fun String.normalizedFxPairKey(): String {
        return trim().uppercase()
    }

    private fun parseZoneId(value: String): ZoneId? {
        return value.trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { ZoneId.of(it) }.getOrNull()
        }
    }

    private fun parseLocalDate(value: String): LocalDate? {
        return value.trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
    }

    private fun parseLocalTime(value: String): LocalTime? {
        return value.trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        }
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

    private val FX_PROVIDERS = setOf("static", "http", "kis-wrapper")
}

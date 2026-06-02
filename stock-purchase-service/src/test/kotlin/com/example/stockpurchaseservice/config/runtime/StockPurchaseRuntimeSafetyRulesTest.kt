package com.example.stockpurchaseservice.config.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StockPurchaseRuntimeSafetyRulesTest {

    @Test
    fun `allows local defaults outside production profiles`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("local"),
                brokerBaseUrl = "http://localhost:8079",
                domesticMockOrder = true,
                overseasMockOrder = true,
                hibernateDdlAuto = "update",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks blank broker endpoint in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = " ",
                domesticMockOrder = false,
                overseasMockOrder = false,
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("non-blank KIS wrapper endpoint"))
    }

    @Test
    fun `blocks local broker endpoint in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "http://127.0.0.1:8079",
                domesticMockOrder = false,
                overseasMockOrder = false,
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("local KIS wrapper endpoint"))
    }

    @Test
    fun `blocks host docker broker endpoint in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "http://host.docker.internal:8079",
                domesticMockOrder = false,
                overseasMockOrder = false,
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("local KIS wrapper endpoint"))
    }

    @Test
    fun `blocks mock order flags in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("live"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = true,
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("mock order flags"))
    }

    @Test
    fun `blocks Hibernate automatic DDL in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("production"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                hibernateDdlAuto = "update",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("Hibernate automatic DDL"))
    }

    @Test
    fun `allows schema validation DDL mode in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                hibernateDdlAuto = "validate",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks disabled order risk controls in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                orderRiskEnabled = false,
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("cannot disable order risk controls"))
    }

    @Test
    fun `blocks disabled critical risk sub-controls in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                sellPositionRiskEnabled = false,
                tradingHoursRiskEnabled = false,
            ),
        )

        assertEquals(2, violations.size)
        assertTrue(violations.any { it.contains("sell position risk control") })
        assertTrue(violations.any { it.contains("trading-hours risk control") })
    }

    @Test
    fun `blocks incomplete production risk guard configuration`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                duplicateOrderKillSwitchEnabled = false,
                accountCashRiskEnabled = false,
                maxOrderNotional = null,
                maxAccountPendingBuyNotional = null,
                maxAccountExposureNotional = null,
                maxDailyOrderCount = null,
                enabledStrategyPrefixes = emptyList(),
                symbolMaxOrderNotional = emptyMap(),
                strategyTradingEnvironmentPrefixes = emptyList(),
            ),
        )

        assertTrue(violations.any { it.contains("duplicate active-order kill switch") })
        assertTrue(violations.any { it.contains("account cash risk control") })
        assertTrue(violations.any { it.contains("max-order-notional") })
        assertTrue(violations.any { it.contains("max-account-pending-buy-notional") })
        assertTrue(violations.any { it.contains("max-account-exposure-notional") })
        assertTrue(violations.any { it.contains("max-daily-order-count") })
        assertTrue(violations.any { it.contains("enabled-strategy-prefixes") })
        assertTrue(violations.any { it.contains("symbol-max-order-notional") })
        assertTrue(violations.any { it.contains("strategy-trading-environments") })
    }

    @Test
    fun `blocks missing static fx rate in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                staticRatesToBase = emptyMap(),
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("positive static FX rate for KRW to USD"))
    }

    @Test
    fun `blocks unsupported fx provider in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                currencyConversionProvider = "manual",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("supported FX provider"))
    }

    @Test
    fun `blocks incomplete fx currency names in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                riskBaseCurrency = "",
                domesticCurrency = " ",
                overseasUsCurrency = "",
            ),
        )

        assertEquals(3, violations.size)
        assertTrue(violations.any { it.contains("risk base currency") })
        assertTrue(violations.any { it.contains("domestic risk currency") })
        assertTrue(violations.any { it.contains("overseas US risk currency") })
    }

    @Test
    fun `blocks http fx provider without base url in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                currencyConversionProvider = "http",
                httpFxRateBaseUrl = "",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("HTTP FX base URL"))
    }

    @Test
    fun `allows configured http fx provider in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                currencyConversionProvider = "http",
                httpFxRateBaseUrl = "https://fx.example.com",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks kis wrapper fx provider without required pair in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                currencyConversionProvider = "kis-wrapper",
                kisWrapperFxPairKeysWithSymbol = emptyList(),
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("KIS wrapper FX pair for KRW to USD"))
    }

    @Test
    fun `allows configured kis wrapper fx provider in production profile`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "https://broker-wrapper.example.com",
                domesticMockOrder = false,
                overseasMockOrder = false,
                currencyConversionProvider = "kis-wrapper",
                kisWrapperFxPairKeysWithSymbol = listOf("KRW-USD"),
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `allows explicitly waived production checks`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "http://localhost:8079",
                domesticMockOrder = true,
                overseasMockOrder = true,
                hibernateDdlAuto = "validate",
                orderRiskEnabled = false,
                allowLocalBrokerEndpointInProduction = true,
                allowMockTradingInProduction = true,
                allowDisabledRiskControlsInProduction = true,
            ),
        )

        assertTrue(violations.isEmpty())
    }

    private fun input(
        activeProfiles: List<String>,
        brokerBaseUrl: String,
        domesticMockOrder: Boolean,
        overseasMockOrder: Boolean,
        hibernateDdlAuto: String? = "validate",
        orderRiskEnabled: Boolean = true,
        sellPositionRiskEnabled: Boolean = true,
        tradingHoursRiskEnabled: Boolean = true,
        duplicateOrderKillSwitchEnabled: Boolean = true,
        accountCashRiskEnabled: Boolean = true,
        maxOrderNotional: Double? = 1_000.0,
        maxAccountPendingBuyNotional: Double? = 5_000.0,
        maxAccountExposureNotional: Double? = 20_000.0,
        maxDailyOrderCount: Long? = 20,
        enabledStrategyPrefixes: Collection<String> = listOf("laor-v4-live"),
        symbolMaxOrderNotional: Map<String, Double> = mapOf("TQQQ" to 1_000.0),
        strategyTradingEnvironmentPrefixes: Collection<String> = listOf("laor-v4-live"),
        currencyConversionProvider: String = "static",
        riskBaseCurrency: String = "USD",
        domesticCurrency: String = "KRW",
        overseasUsCurrency: String = "USD",
        staticRatesToBase: Map<String, Double> = mapOf("KRW" to 0.001),
        httpFxRateBaseUrl: String = "https://fx.example.com",
        kisWrapperFxPairKeysWithSymbol: Collection<String> = listOf("KRW-USD"),
        allowLocalBrokerEndpointInProduction: Boolean = false,
        allowMockTradingInProduction: Boolean = false,
        allowDisabledRiskControlsInProduction: Boolean = false,
    ) = StockPurchaseRuntimeSafetyRules.Input(
        activeProfiles = activeProfiles,
        enabled = true,
        productionProfiles = listOf("prod", "production", "live"),
        brokerBaseUrl = brokerBaseUrl,
        domesticMockOrder = domesticMockOrder,
        overseasMockOrder = overseasMockOrder,
        hibernateDdlAuto = hibernateDdlAuto,
        orderRiskEnabled = orderRiskEnabled,
        sellPositionRiskEnabled = sellPositionRiskEnabled,
        tradingHoursRiskEnabled = tradingHoursRiskEnabled,
        duplicateOrderKillSwitchEnabled = duplicateOrderKillSwitchEnabled,
        accountCashRiskEnabled = accountCashRiskEnabled,
        maxOrderNotional = maxOrderNotional,
        maxAccountPendingBuyNotional = maxAccountPendingBuyNotional,
        maxAccountExposureNotional = maxAccountExposureNotional,
        maxDailyOrderCount = maxDailyOrderCount,
        enabledStrategyPrefixes = enabledStrategyPrefixes,
        symbolMaxOrderNotional = symbolMaxOrderNotional,
        strategyTradingEnvironmentPrefixes = strategyTradingEnvironmentPrefixes,
        currencyConversionProvider = currencyConversionProvider,
        riskBaseCurrency = riskBaseCurrency,
        domesticCurrency = domesticCurrency,
        overseasUsCurrency = overseasUsCurrency,
        staticRatesToBase = staticRatesToBase,
        httpFxRateBaseUrl = httpFxRateBaseUrl,
        kisWrapperFxPairKeysWithSymbol = kisWrapperFxPairKeysWithSymbol,
        allowLocalBrokerEndpointInProduction = allowLocalBrokerEndpointInProduction,
        allowMockTradingInProduction = allowMockTradingInProduction,
        allowDisabledRiskControlsInProduction = allowDisabledRiskControlsInProduction,
    )
}

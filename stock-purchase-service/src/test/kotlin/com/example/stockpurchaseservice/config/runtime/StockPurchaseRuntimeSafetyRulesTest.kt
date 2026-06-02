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
        allowLocalBrokerEndpointInProduction = allowLocalBrokerEndpointInProduction,
        allowMockTradingInProduction = allowMockTradingInProduction,
        allowDisabledRiskControlsInProduction = allowDisabledRiskControlsInProduction,
    )
}

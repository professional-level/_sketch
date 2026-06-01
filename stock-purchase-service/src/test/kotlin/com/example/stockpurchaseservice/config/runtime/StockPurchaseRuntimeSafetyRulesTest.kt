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
    fun `allows explicitly waived production checks`() {
        val violations = StockPurchaseRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                brokerBaseUrl = "http://localhost:8079",
                domesticMockOrder = true,
                overseasMockOrder = true,
                allowLocalBrokerEndpointInProduction = true,
                allowMockTradingInProduction = true,
            ),
        )

        assertTrue(violations.isEmpty())
    }

    private fun input(
        activeProfiles: List<String>,
        brokerBaseUrl: String,
        domesticMockOrder: Boolean,
        overseasMockOrder: Boolean,
        allowLocalBrokerEndpointInProduction: Boolean = false,
        allowMockTradingInProduction: Boolean = false,
    ) = StockPurchaseRuntimeSafetyRules.Input(
        activeProfiles = activeProfiles,
        enabled = true,
        productionProfiles = listOf("prod", "production", "live"),
        brokerBaseUrl = brokerBaseUrl,
        domesticMockOrder = domesticMockOrder,
        overseasMockOrder = overseasMockOrder,
        allowLocalBrokerEndpointInProduction = allowLocalBrokerEndpointInProduction,
        allowMockTradingInProduction = allowMockTradingInProduction,
    )
}

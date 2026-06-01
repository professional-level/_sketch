package com.example.strategyexecutionservice.config.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrategyExecutionRuntimeSafetyRulesTest {

    @Test
    fun `allows local defaults outside production profiles`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("local"),
                temporalTarget = "127.0.0.1:7233",
                marketDataBaseUrl = "http://localhost:8079",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks local temporal target in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "127.0.0.1:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("local Temporal target"))
    }

    @Test
    fun `blocks local market data endpoint in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("live"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "http://localhost:8079",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("local market-data KIS wrapper endpoint"))
    }

    @Test
    fun `allows explicitly waived production checks`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "127.0.0.1:7233",
                marketDataBaseUrl = "http://localhost:8079",
                allowLocalTemporalTargetInProduction = true,
                allowLocalMarketDataEndpointInProduction = true,
            ),
        )

        assertTrue(violations.isEmpty())
    }

    private fun input(
        activeProfiles: List<String>,
        temporalTarget: String,
        marketDataBaseUrl: String,
        allowLocalTemporalTargetInProduction: Boolean = false,
        allowLocalMarketDataEndpointInProduction: Boolean = false,
    ) = StrategyExecutionRuntimeSafetyRules.Input(
        activeProfiles = activeProfiles,
        enabled = true,
        productionProfiles = listOf("prod", "production", "live"),
        temporalEnabled = true,
        temporalTarget = temporalTarget,
        marketDataBaseUrl = marketDataBaseUrl,
        allowLocalTemporalTargetInProduction = allowLocalTemporalTargetInProduction,
        allowLocalMarketDataEndpointInProduction = allowLocalMarketDataEndpointInProduction,
    )
}

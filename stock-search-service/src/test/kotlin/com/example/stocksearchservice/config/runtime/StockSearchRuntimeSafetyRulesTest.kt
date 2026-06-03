package com.example.stocksearchservice.config.runtime

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StockSearchRuntimeSafetyRulesTest {

    @Test
    fun `allows local defaults outside production profiles`() {
        val violations = StockSearchRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("local"),
                temporalTarget = "127.0.0.1:7233",
                hibernateDdlAuto = "update",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks blank temporal target in production profile`() {
        val violations = StockSearchRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = " ",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("non-blank Temporal target"))
    }

    @Test
    fun `blocks local temporal target in production profile`() {
        val violations = StockSearchRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "127.0.0.1:7233",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("local Temporal target"))
    }

    @Test
    fun `blocks Hibernate automatic DDL in production profile`() {
        val violations = StockSearchRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("production"),
                temporalTarget = "temporal.example.com:7233",
                hibernateDdlAuto = "update",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("Hibernate automatic DDL"))
    }

    @Test
    fun `allows schema validation DDL mode in production profile`() {
        val violations = StockSearchRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                hibernateDdlAuto = "validate",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks incomplete temporal worker and schedule configuration in production profile`() {
        val violations = StockSearchRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                temporalNamespace = " ",
                temporalTaskQueue = "",
                topVolumeScheduleId = " ",
                topVolumeScheduleInterval = Duration.ZERO,
            ),
        )

        assertEquals(4, violations.size)
        assertTrue(violations.any { it.contains("Temporal namespace") })
        assertTrue(violations.any { it.contains("Temporal task queue") })
        assertTrue(violations.any { it.contains("top-volume Temporal schedule id") })
        assertTrue(violations.any { it.contains("positive top-volume Temporal schedule interval") })
    }

    @Test
    fun `blocks too frequent top volume schedule in production profile`() {
        val violations = StockSearchRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                topVolumeScheduleInterval = Duration.ofSeconds(30),
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("must be at least PT1M"))
    }

    @Test
    fun `allows disabled top volume schedule without schedule id in production profile`() {
        val violations = StockSearchRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                topVolumeScheduleEnabled = false,
                topVolumeScheduleId = "",
                topVolumeScheduleInterval = Duration.ZERO,
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks application secret property source in production profile`() {
        val violations = StockSearchRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                applicationSecretPropertySources = listOf("class path resource [application-secret.properties]"),
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("application-secret.properties"))
    }

    @Test
    fun `allows explicitly waived production checks`() {
        val violations = StockSearchRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "127.0.0.1:7233",
                applicationSecretPropertySources = listOf("class path resource [application-secret.properties]"),
                allowLocalTemporalTargetInProduction = true,
                allowApplicationSecretPropertySourceInProduction = true,
            ),
        )

        assertTrue(violations.isEmpty())
    }

    private fun input(
        activeProfiles: List<String>,
        temporalTarget: String,
        hibernateDdlAuto: String? = "validate",
        temporalEnabled: Boolean = true,
        temporalNamespace: String = "default",
        temporalTaskQueue: String = "stock-search-scheduler",
        topVolumeScheduleEnabled: Boolean = true,
        topVolumeScheduleId: String = "stock-search-top-volume-stocks",
        topVolumeScheduleInterval: Duration = Duration.ofMinutes(1),
        applicationSecretPropertySources: List<String> = emptyList(),
        allowLocalTemporalTargetInProduction: Boolean = false,
        allowApplicationSecretPropertySourceInProduction: Boolean = false,
    ) = StockSearchRuntimeSafetyRules.Input(
        activeProfiles = activeProfiles,
        enabled = true,
        productionProfiles = listOf("prod", "production", "live"),
        temporalEnabled = temporalEnabled,
        temporalTarget = temporalTarget,
        temporalNamespace = temporalNamespace,
        temporalTaskQueue = temporalTaskQueue,
        topVolumeScheduleEnabled = topVolumeScheduleEnabled,
        topVolumeScheduleId = topVolumeScheduleId,
        topVolumeScheduleInterval = topVolumeScheduleInterval,
        hibernateDdlAuto = hibernateDdlAuto,
        applicationSecretPropertySources = applicationSecretPropertySources,
        allowLocalTemporalTargetInProduction = allowLocalTemporalTargetInProduction,
        allowApplicationSecretPropertySourceInProduction = allowApplicationSecretPropertySourceInProduction,
    )
}

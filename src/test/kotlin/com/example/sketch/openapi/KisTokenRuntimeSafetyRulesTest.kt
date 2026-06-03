package com.example.sketch.openapi

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KisTokenRuntimeSafetyRulesTest {

    @Test
    fun `allows local file token persistence outside production profiles`() {
        val violations = KisTokenRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("local"),
                tokenPersistenceEnabled = true,
                tokenPersistenceType = "file",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks local file token persistence in production profiles`() {
        val violations = KisTokenRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                tokenPersistenceEnabled = true,
                tokenPersistenceType = "file",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("local-file KIS token persistence"))
    }

    @Test
    fun `allows jdbc token persistence in production profiles`() {
        val violations = KisTokenRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("live"),
                tokenPersistenceEnabled = true,
                tokenPersistenceType = "jdbc",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks unsafe token timing configuration in production profiles`() {
        val violations = KisTokenRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                tokenPersistenceEnabled = false,
                tokenPersistenceType = "file",
                tokenRefreshBeforeExpiry = Duration.ZERO,
                tokenFallbackTtl = Duration.ofSeconds(-1),
            ),
        )

        assertEquals(2, violations.size)
        assertTrue(violations.any { it.contains("refresh-before-expiry") })
        assertTrue(violations.any { it.contains("fallback TTL") })
    }

    @Test
    fun `blocks unsupported token persistence type in production profiles`() {
        val violations = KisTokenRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                tokenPersistenceEnabled = true,
                tokenPersistenceType = "redis",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("supported KIS token persistence type"))
    }

    @Test
    fun `blocks unsafe jdbc token lock settings in production profiles`() {
        val violations = KisTokenRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                tokenPersistenceEnabled = true,
                tokenPersistenceType = "jdbc",
                tokenPersistenceLockTtl = Duration.ZERO,
                tokenPersistenceLockWaitTimeout = Duration.ofMillis(10),
                tokenPersistenceLockRetryDelay = Duration.ofMillis(20),
            ),
        )

        assertEquals(2, violations.size)
        assertTrue(violations.any { it.contains("JDBC lock TTL") })
        assertTrue(violations.any { it.contains("retry delay cannot exceed lock wait timeout") })
    }

    @Test
    fun `blocks Hibernate automatic DDL in production profiles`() {
        val violations = KisTokenRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                tokenPersistenceEnabled = false,
                tokenPersistenceType = "file",
                hibernateDdlAuto = "update",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("Hibernate automatic DDL"))
    }

    @Test
    fun `allows schema validation DDL mode in production profiles`() {
        val violations = KisTokenRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("production"),
                tokenPersistenceEnabled = false,
                tokenPersistenceType = "file",
                hibernateDdlAuto = "validate",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `allows explicitly waived local file token persistence in production profiles`() {
        val violations = KisTokenRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("production"),
                tokenPersistenceEnabled = true,
                tokenPersistenceType = "file",
                allowFileTokenPersistenceInProduction = true,
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks application secret property source in production profiles`() {
        val violations = KisTokenRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                tokenPersistenceEnabled = false,
                tokenPersistenceType = "jdbc",
                applicationSecretPropertySources = listOf("class path resource [application-secret.properties]"),
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("application-secret.properties"))
    }

    @Test
    fun `allows explicitly waived application secret property source in production profiles`() {
        val violations = KisTokenRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                tokenPersistenceEnabled = false,
                tokenPersistenceType = "jdbc",
                applicationSecretPropertySources = listOf("class path resource [application-secret.properties]"),
                allowApplicationSecretPropertySourceInProduction = true,
            ),
        )

        assertTrue(violations.isEmpty())
    }

    private fun input(
        activeProfiles: List<String>,
        tokenPersistenceEnabled: Boolean,
        tokenPersistenceType: String,
        tokenRefreshBeforeExpiry: Duration = Duration.ofMinutes(10),
        tokenFallbackTtl: Duration = Duration.ofHours(23),
        tokenPersistenceLockTtl: Duration = Duration.ofSeconds(30),
        tokenPersistenceLockWaitTimeout: Duration = Duration.ofSeconds(10),
        tokenPersistenceLockRetryDelay: Duration = Duration.ofMillis(100),
        hibernateDdlAuto: String? = "validate",
        allowFileTokenPersistenceInProduction: Boolean = false,
        applicationSecretPropertySources: List<String> = emptyList(),
        allowApplicationSecretPropertySourceInProduction: Boolean = false,
    ) = KisTokenRuntimeSafetyRules.Input(
        activeProfiles = activeProfiles,
        enabled = true,
        productionProfiles = listOf("prod", "production", "live"),
        tokenRefreshBeforeExpiry = tokenRefreshBeforeExpiry,
        tokenFallbackTtl = tokenFallbackTtl,
        tokenPersistenceEnabled = tokenPersistenceEnabled,
        tokenPersistenceType = tokenPersistenceType,
        tokenPersistenceLockTtl = tokenPersistenceLockTtl,
        tokenPersistenceLockWaitTimeout = tokenPersistenceLockWaitTimeout,
        tokenPersistenceLockRetryDelay = tokenPersistenceLockRetryDelay,
        hibernateDdlAuto = hibernateDdlAuto,
        allowFileTokenPersistenceInProduction = allowFileTokenPersistenceInProduction,
        applicationSecretPropertySources = applicationSecretPropertySources,
        allowApplicationSecretPropertySourceInProduction = allowApplicationSecretPropertySourceInProduction,
    )
}

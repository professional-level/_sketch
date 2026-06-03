package com.example.sketch.openapi

import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class KisTokenRuntimeSafetyValidator(
    private val environment: Environment,
    private val properties: KisTokenProperties,
    @Value("\${akra.runtime.safety.enabled:true}")
    private val enabled: Boolean,
    @Value("\${akra.runtime.safety.production-profiles:prod,production,live}")
    private val productionProfiles: String,
    @Value("\${akra.runtime.safety.allow-file-token-persistence-in-production:false}")
    private val allowFileTokenPersistenceInProduction: Boolean,
    @Value("\${akra.runtime.safety.allow-application-secret-property-source-in-production:false}")
    private val allowApplicationSecretPropertySourceInProduction: Boolean,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val violations = KisTokenRuntimeSafetyRules.validate(
            KisTokenRuntimeSafetyRules.Input(
                activeProfiles = environment.activeProfiles.toList(),
                enabled = enabled,
                productionProfiles = productionProfiles.split(","),
                tokenRefreshBeforeExpiry = properties.refreshBeforeExpiry,
                tokenFallbackTtl = properties.fallbackTtl,
                tokenPersistenceEnabled = properties.persistence.enabled,
                tokenPersistenceType = properties.persistence.type,
                tokenPersistenceLockTtl = properties.persistence.lockTtl,
                tokenPersistenceLockWaitTimeout = properties.persistence.lockWaitTimeout,
                tokenPersistenceLockRetryDelay = properties.persistence.lockRetryDelay,
                hibernateDdlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto"),
                allowFileTokenPersistenceInProduction = allowFileTokenPersistenceInProduction,
                applicationSecretPropertySources = environment.applicationSecretPropertySourceNames(),
                allowApplicationSecretPropertySourceInProduction = allowApplicationSecretPropertySourceInProduction,
            ),
        )

        if (violations.isNotEmpty()) {
            throw IllegalStateException(
                violations.joinToString(
                    separator = System.lineSeparator() + "- ",
                    prefix = "Unsafe KIS token runtime configuration:" +
                        System.lineSeparator() + "- ",
                ),
            )
        }
    }

    private fun Environment.applicationSecretPropertySourceNames(): List<String> {
        return (this as? ConfigurableEnvironment)
            ?.propertySources
            ?.asSequence()
            ?.map { it.name }
            ?.filter { it.contains("application-secret.properties", ignoreCase = true) }
            ?.toList()
            ?: emptyList()
    }
}

object KisTokenRuntimeSafetyRules {
    data class Input(
        val activeProfiles: List<String>,
        val enabled: Boolean,
        val productionProfiles: List<String>,
        val tokenRefreshBeforeExpiry: Duration,
        val tokenFallbackTtl: Duration,
        val tokenPersistenceEnabled: Boolean,
        val tokenPersistenceType: String,
        val tokenPersistenceLockTtl: Duration,
        val tokenPersistenceLockWaitTimeout: Duration,
        val tokenPersistenceLockRetryDelay: Duration,
        val hibernateDdlAuto: String?,
        val allowFileTokenPersistenceInProduction: Boolean,
        val applicationSecretPropertySources: List<String>,
        val allowApplicationSecretPropertySourceInProduction: Boolean,
    )

    fun validate(input: Input): List<String> {
        if (!input.enabled || !isProduction(input.activeProfiles, input.productionProfiles)) {
            return emptyList()
        }

        val violations = mutableListOf<String>()
        val normalizedPersistenceType = input.tokenPersistenceType.trim().lowercase()
        if (!input.tokenRefreshBeforeExpiry.isPositive()) {
            violations += "prod/live profile must configure a positive KIS token refresh-before-expiry " +
                "(akra.kis.token.refresh-before-expiry=${input.tokenRefreshBeforeExpiry})"
        }
        if (!input.tokenFallbackTtl.isPositive()) {
            violations += "prod/live profile must configure a positive KIS token fallback TTL " +
                "(akra.kis.token.fallback-ttl=${input.tokenFallbackTtl})"
        }
        if (input.tokenPersistenceEnabled && normalizedPersistenceType !in SUPPORTED_PERSISTENCE_TYPES) {
            violations += "prod/live profile must configure a supported KIS token persistence type " +
                "(akra.kis.token.persistence.type=${input.tokenPersistenceType}; supported=file|jdbc)"
        }
        if (
            input.tokenPersistenceEnabled &&
            normalizedPersistenceType == "file" &&
            !input.allowFileTokenPersistenceInProduction
        ) {
            violations +=
                "prod/live profile cannot use local-file KIS token persistence " +
                    "(akra.kis.token.persistence.type=file); use jdbc or an external secret/token store, " +
                    "or set akra.runtime.safety.allow-file-token-persistence-in-production=true for a controlled waiver"
        }
        if (input.tokenPersistenceEnabled && normalizedPersistenceType == "jdbc") {
            violations += validateJdbcPersistenceLock(input)
        }

        if (!isSafeHibernateDdlAuto(input.hibernateDdlAuto)) {
            violations += "prod/live profile cannot use Hibernate automatic DDL " +
                "(spring.jpa.hibernate.ddl-auto=${input.hibernateDdlAuto}); apply migrations explicitly and use none or validate"
        }

        if (
            input.applicationSecretPropertySources.isNotEmpty() &&
            !input.allowApplicationSecretPropertySourceInProduction
        ) {
            violations +=
                "prod/live profile cannot load local application-secret.properties property sources " +
                    "(${input.applicationSecretPropertySources.joinToString()}); provide KIS secrets through " +
                    "environment variables, *_FILE secret mounts, or a managed secret source"
        }

        return violations
    }

    private fun validateJdbcPersistenceLock(input: Input): List<String> {
        val violations = mutableListOf<String>()
        if (!input.tokenPersistenceLockTtl.isPositive()) {
            violations += "prod/live profile must configure a positive KIS token JDBC lock TTL " +
                "(akra.kis.token.persistence.lock-ttl=${input.tokenPersistenceLockTtl})"
        }
        if (!input.tokenPersistenceLockWaitTimeout.isPositive()) {
            violations += "prod/live profile must configure a positive KIS token JDBC lock wait timeout " +
                "(akra.kis.token.persistence.lock-wait-timeout=${input.tokenPersistenceLockWaitTimeout})"
        }
        if (!input.tokenPersistenceLockRetryDelay.isPositive()) {
            violations += "prod/live profile must configure a positive KIS token JDBC lock retry delay " +
                "(akra.kis.token.persistence.lock-retry-delay=${input.tokenPersistenceLockRetryDelay})"
        }
        if (
            input.tokenPersistenceLockWaitTimeout.isPositive() &&
            input.tokenPersistenceLockRetryDelay.isPositive() &&
            input.tokenPersistenceLockRetryDelay > input.tokenPersistenceLockWaitTimeout
        ) {
            violations += "prod/live profile KIS token JDBC lock retry delay cannot exceed lock wait timeout " +
                "(akra.kis.token.persistence.lock-retry-delay=${input.tokenPersistenceLockRetryDelay}, " +
                "akra.kis.token.persistence.lock-wait-timeout=${input.tokenPersistenceLockWaitTimeout})"
        }
        return violations
    }

    private fun isSafeHibernateDdlAuto(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return normalized.isEmpty() || normalized == "none" || normalized == "validate"
    }

    private fun Duration.isPositive(): Boolean {
        return !isZero && !isNegative
    }

    private fun isProduction(activeProfiles: List<String>, productionProfiles: List<String>): Boolean {
        val productionProfileSet = productionProfiles.map { it.trim().lowercase() }.toSet()
        return activeProfiles
            .map { it.trim().lowercase() }
            .any { it in productionProfileSet }
    }

    private val SUPPORTED_PERSISTENCE_TYPES = setOf("file", "jdbc")
}

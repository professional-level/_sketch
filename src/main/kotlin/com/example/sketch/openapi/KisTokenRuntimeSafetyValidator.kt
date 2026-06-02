package com.example.sketch.openapi

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
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
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val violations = KisTokenRuntimeSafetyRules.validate(
            KisTokenRuntimeSafetyRules.Input(
                activeProfiles = environment.activeProfiles.toList(),
                enabled = enabled,
                productionProfiles = productionProfiles.split(","),
                tokenPersistenceEnabled = properties.persistence.enabled,
                tokenPersistenceType = properties.persistence.type,
                allowFileTokenPersistenceInProduction = allowFileTokenPersistenceInProduction,
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
}

object KisTokenRuntimeSafetyRules {
    data class Input(
        val activeProfiles: List<String>,
        val enabled: Boolean,
        val productionProfiles: List<String>,
        val tokenPersistenceEnabled: Boolean,
        val tokenPersistenceType: String,
        val allowFileTokenPersistenceInProduction: Boolean,
    )

    fun validate(input: Input): List<String> {
        if (!input.enabled || !isProduction(input.activeProfiles, input.productionProfiles)) {
            return emptyList()
        }

        val normalizedPersistenceType = input.tokenPersistenceType.trim().lowercase()
        if (
            input.tokenPersistenceEnabled &&
            normalizedPersistenceType == "file" &&
            !input.allowFileTokenPersistenceInProduction
        ) {
            return listOf(
                "prod/live profile cannot use local-file KIS token persistence " +
                    "(akra.kis.token.persistence.type=file); use jdbc or an external secret/token store, " +
                    "or set akra.runtime.safety.allow-file-token-persistence-in-production=true for a controlled waiver",
            )
        }

        return emptyList()
    }

    private fun isProduction(activeProfiles: List<String>, productionProfiles: List<String>): Boolean {
        val productionProfileSet = productionProfiles.map { it.trim().lowercase() }.toSet()
        return activeProfiles
            .map { it.trim().lowercase() }
            .any { it in productionProfileSet }
    }
}

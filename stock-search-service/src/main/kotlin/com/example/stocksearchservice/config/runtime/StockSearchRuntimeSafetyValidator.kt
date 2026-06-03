package com.example.stocksearchservice.config.runtime

import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class StockSearchRuntimeSafetyValidator(
    private val environment: Environment,
    private val properties: StockSearchRuntimeSafetyProperties,
    @Value("\${akra.temporal.enabled:false}")
    private val temporalEnabled: Boolean,
    @Value("\${akra.temporal.target:127.0.0.1:7233}")
    private val temporalTarget: String,
    @Value("\${akra.temporal.namespace:default}")
    private val temporalNamespace: String,
    @Value("\${akra.temporal.task-queue:stock-search-scheduler}")
    private val temporalTaskQueue: String,
    @Value("\${akra.temporal.schedules.top-volume.enabled:true}")
    private val topVolumeScheduleEnabled: Boolean,
    @Value("\${akra.temporal.schedules.top-volume.schedule-id:stock-search-top-volume-stocks}")
    private val topVolumeScheduleId: String,
    @Value("\${akra.temporal.schedules.top-volume.interval:1m}")
    private val topVolumeScheduleInterval: Duration,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val violations = StockSearchRuntimeSafetyRules.validate(
            StockSearchRuntimeSafetyRules.Input(
                activeProfiles = environment.activeProfiles.toList(),
                enabled = properties.enabled,
                productionProfiles = properties.productionProfiles,
                temporalEnabled = temporalEnabled,
                temporalTarget = temporalTarget,
                temporalNamespace = temporalNamespace,
                temporalTaskQueue = temporalTaskQueue,
                topVolumeScheduleEnabled = topVolumeScheduleEnabled,
                topVolumeScheduleId = topVolumeScheduleId,
                topVolumeScheduleInterval = topVolumeScheduleInterval,
                hibernateDdlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto"),
                applicationSecretPropertySources = environment.applicationSecretPropertySourceNames(),
                allowLocalTemporalTargetInProduction = properties.allowLocalTemporalTargetInProduction,
                allowApplicationSecretPropertySourceInProduction =
                    properties.allowApplicationSecretPropertySourceInProduction,
            ),
        )

        if (violations.isNotEmpty()) {
            throw IllegalStateException(
                violations.joinToString(
                    separator = System.lineSeparator() + "- ",
                    prefix = "Unsafe stock-search-service runtime configuration:" +
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

object StockSearchRuntimeSafetyRules {
    data class Input(
        val activeProfiles: List<String>,
        val enabled: Boolean,
        val productionProfiles: List<String>,
        val temporalEnabled: Boolean,
        val temporalTarget: String,
        val temporalNamespace: String,
        val temporalTaskQueue: String,
        val topVolumeScheduleEnabled: Boolean,
        val topVolumeScheduleId: String,
        val topVolumeScheduleInterval: Duration,
        val hibernateDdlAuto: String?,
        val applicationSecretPropertySources: List<String>,
        val allowLocalTemporalTargetInProduction: Boolean,
        val allowApplicationSecretPropertySourceInProduction: Boolean,
    )

    fun validate(input: Input): List<String> {
        if (!input.enabled || !isProduction(input.activeProfiles, input.productionProfiles)) {
            return emptyList()
        }

        val violations = mutableListOf<String>()
        if (input.temporalEnabled && input.temporalTarget.isBlank()) {
            violations += "prod/live profile must configure a non-blank Temporal target " +
                "(akra.temporal.target)"
        }
        if (input.temporalEnabled && input.temporalNamespace.isBlank()) {
            violations += "prod/live profile must configure a non-blank Temporal namespace " +
                "(akra.temporal.namespace)"
        }
        if (input.temporalEnabled && input.temporalTaskQueue.isBlank()) {
            violations += "prod/live profile must configure a non-blank Temporal task queue " +
                "(akra.temporal.task-queue)"
        }
        if (
            input.temporalEnabled &&
            !input.allowLocalTemporalTargetInProduction &&
            isLocalEndpoint(input.temporalTarget)
        ) {
            violations += "prod/live profile cannot use a local Temporal target " +
                "(akra.temporal.target=${input.temporalTarget})"
        }
        if (input.temporalEnabled && input.topVolumeScheduleEnabled) {
            validateTopVolumeSchedule(input, violations)
        }

        if (!isSafeHibernateDdlAuto(input.hibernateDdlAuto)) {
            violations += "prod/live profile cannot use Hibernate automatic DDL " +
                "(spring.jpa.hibernate.ddl-auto=${input.hibernateDdlAuto}); apply migrations explicitly and use none or validate"
        }

        if (
            input.applicationSecretPropertySources.isNotEmpty() &&
            !input.allowApplicationSecretPropertySourceInProduction
        ) {
            violations += "prod/live profile cannot load local application-secret.properties property sources " +
                "(${input.applicationSecretPropertySources.joinToString()}); provide secrets through environment " +
                "variables, *_FILE secret mounts, or a managed secret source"
        }

        return violations
    }

    private fun validateTopVolumeSchedule(input: Input, violations: MutableList<String>) {
        if (input.topVolumeScheduleId.isBlank()) {
            violations += "prod/live profile must configure a non-blank top-volume Temporal schedule id " +
                "(akra.temporal.schedules.top-volume.schedule-id)"
        }
        if (input.topVolumeScheduleInterval.isZero || input.topVolumeScheduleInterval.isNegative) {
            violations += "prod/live profile must configure a positive top-volume Temporal schedule interval " +
                "(akra.temporal.schedules.top-volume.interval=${input.topVolumeScheduleInterval})"
        } else if (input.topVolumeScheduleInterval < MIN_TOP_VOLUME_SCHEDULE_INTERVAL) {
            violations += "prod/live profile top-volume Temporal schedule interval must be at least " +
                "$MIN_TOP_VOLUME_SCHEDULE_INTERVAL " +
                "(akra.temporal.schedules.top-volume.interval=${input.topVolumeScheduleInterval})"
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

    private val MIN_TOP_VOLUME_SCHEDULE_INTERVAL: Duration = Duration.ofMinutes(1)
}

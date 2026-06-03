package com.example.strategyexecutionservice.config.runtime

import com.example.strategyexecutionservice.config.calendar.TradingCalendarProperties
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class StrategyExecutionRuntimeSafetyValidator(
    private val environment: Environment,
    private val properties: StrategyExecutionRuntimeSafetyProperties,
    private val tradingCalendarProperties: TradingCalendarProperties,
    @Value("\${akra.temporal.enabled:false}")
    private val temporalEnabled: Boolean,
    @Value("\${akra.temporal.target:127.0.0.1:7233}")
    private val temporalTarget: String,
    @Value("\${akra.market-data.kis-open-api.base-url:http://localhost:8079}")
    private val marketDataBaseUrl: String,
    @Value("\${akra.order-intent.default-trading-environment:MOCK}")
    private val defaultOrderIntentTradingEnvironment: String,
    @Value("\${akra.temporal.schedules.active-executions.enabled:true}")
    private val activeExecutionsScheduleEnabled: Boolean,
    @Value("\${akra.temporal.schedules.active-executions.schedule-id:strategy-execution-active-executions-daily}")
    private val activeExecutionsScheduleId: String,
    @Value("\${akra.temporal.schedules.active-executions.hour:9}")
    private val activeExecutionsScheduleHour: Int,
    @Value("\${akra.temporal.schedules.active-executions.minute:30}")
    private val activeExecutionsScheduleMinute: Int,
    @Value("\${akra.temporal.schedules.active-executions.second:0}")
    private val activeExecutionsScheduleSecond: Int,
    @Value("\${akra.temporal.schedules.active-executions.time-zone:America/New_York}")
    private val activeExecutionsScheduleTimeZone: String,
    @Value("\${akra.temporal.schedules.active-executions.execution-run-id-prefix:ACTIVE_STRATEGIES_DAILY}")
    private val activeExecutionsScheduleRunIdPrefix: String,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val usCalendar = tradingCalendarProperties.us
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            StrategyExecutionRuntimeSafetyRules.Input(
                activeProfiles = environment.activeProfiles.toList(),
                enabled = properties.enabled,
                productionProfiles = properties.productionProfiles,
                temporalEnabled = temporalEnabled,
                temporalTarget = temporalTarget,
                marketDataBaseUrl = marketDataBaseUrl,
                hibernateDdlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto"),
                defaultOrderIntentTradingEnvironment = defaultOrderIntentTradingEnvironment,
                activeExecutionsScheduleEnabled = activeExecutionsScheduleEnabled,
                activeExecutionsScheduleId = activeExecutionsScheduleId,
                activeExecutionsScheduleHour = activeExecutionsScheduleHour,
                activeExecutionsScheduleMinute = activeExecutionsScheduleMinute,
                activeExecutionsScheduleSecond = activeExecutionsScheduleSecond,
                activeExecutionsScheduleTimeZone = activeExecutionsScheduleTimeZone,
                activeExecutionsScheduleRunIdPrefix = activeExecutionsScheduleRunIdPrefix,
                usTradingCalendarEnabled = usCalendar.enabled,
                defaultUsEquityCalendarEnabled = usCalendar.defaultUsEquityCalendarEnabled,
                usTradingCalendarZoneId = usCalendar.zoneId,
                usTradingCalendarRegularOpen = usCalendar.regularOpen,
                usTradingCalendarRegularClose = usCalendar.regularClose,
                usTradingCalendarHolidays = usCalendar.holidays,
                usTradingCalendarEarlyCloseDays = usCalendar.earlyCloseDays,
                usTradingCalendarEarlyCloseTime = usCalendar.earlyCloseTime,
                usTradingCalendarEarlyCloseTimes = usCalendar.earlyCloseTimes.toMap(),
                applicationSecretPropertySources = environment.applicationSecretPropertySourceNames(),
                allowLocalTemporalTargetInProduction = properties.allowLocalTemporalTargetInProduction,
                allowLocalMarketDataEndpointInProduction = properties.allowLocalMarketDataEndpointInProduction,
                allowMockOrderIntentInProduction = properties.allowMockOrderIntentInProduction,
                allowDisabledTradingCalendarInProduction = properties.allowDisabledTradingCalendarInProduction,
                allowApplicationSecretPropertySourceInProduction =
                    properties.allowApplicationSecretPropertySourceInProduction,
            ),
        )

        if (violations.isNotEmpty()) {
            throw IllegalStateException(
                violations.joinToString(
                    separator = System.lineSeparator() + "- ",
                    prefix = "Unsafe strategy-execution-service runtime configuration:" +
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

object StrategyExecutionRuntimeSafetyRules {
    data class Input(
        val activeProfiles: List<String>,
        val enabled: Boolean,
        val productionProfiles: List<String>,
        val temporalEnabled: Boolean,
        val temporalTarget: String,
        val marketDataBaseUrl: String,
        val hibernateDdlAuto: String?,
        val defaultOrderIntentTradingEnvironment: String,
        val activeExecutionsScheduleEnabled: Boolean,
        val activeExecutionsScheduleId: String,
        val activeExecutionsScheduleHour: Int,
        val activeExecutionsScheduleMinute: Int,
        val activeExecutionsScheduleSecond: Int,
        val activeExecutionsScheduleTimeZone: String,
        val activeExecutionsScheduleRunIdPrefix: String,
        val usTradingCalendarEnabled: Boolean,
        val defaultUsEquityCalendarEnabled: Boolean,
        val usTradingCalendarZoneId: String,
        val usTradingCalendarRegularOpen: String,
        val usTradingCalendarRegularClose: String,
        val usTradingCalendarHolidays: List<String>,
        val usTradingCalendarEarlyCloseDays: List<String>,
        val usTradingCalendarEarlyCloseTime: String?,
        val usTradingCalendarEarlyCloseTimes: Map<String, String>,
        val applicationSecretPropertySources: List<String>,
        val allowLocalTemporalTargetInProduction: Boolean,
        val allowLocalMarketDataEndpointInProduction: Boolean,
        val allowMockOrderIntentInProduction: Boolean,
        val allowDisabledTradingCalendarInProduction: Boolean,
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
        if (
            input.temporalEnabled &&
            !input.allowLocalTemporalTargetInProduction &&
            isLocalEndpoint(input.temporalTarget)
        ) {
            violations += "prod/live profile cannot use a local Temporal target " +
                "(akra.temporal.target=${input.temporalTarget})"
        }

        if (input.marketDataBaseUrl.isBlank()) {
            violations += "prod/live profile must configure a non-blank market-data KIS wrapper endpoint " +
                "(akra.market-data.kis-open-api.base-url)"
        }
        if (!input.allowLocalMarketDataEndpointInProduction && isLocalEndpoint(input.marketDataBaseUrl)) {
            violations += "prod/live profile cannot use a local market-data KIS wrapper endpoint " +
                "(akra.market-data.kis-open-api.base-url=${input.marketDataBaseUrl})"
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

        if (
            !input.allowMockOrderIntentInProduction &&
            input.defaultOrderIntentTradingEnvironment.trim().uppercase() != "LIVE"
        ) {
            violations += "prod/live profile must default order intents to LIVE " +
                "(akra.order-intent.default-trading-environment=${input.defaultOrderIntentTradingEnvironment})"
        }

        if (!input.allowDisabledTradingCalendarInProduction) {
            if (!input.usTradingCalendarEnabled) {
                violations += "prod/live profile cannot disable US trading calendar " +
                    "(akra.trading-calendar.us.enabled=false)"
            } else if (!input.defaultUsEquityCalendarEnabled) {
                violations += "prod/live profile cannot disable default US equity calendar " +
                    "(akra.trading-calendar.us.default-us-equity-calendar-enabled=false)"
            }
        }

        if (input.usTradingCalendarEnabled) {
            validateUsTradingCalendar(input, violations)
        }
        if (input.temporalEnabled && input.activeExecutionsScheduleEnabled) {
            validateActiveExecutionSchedule(input, violations)
        }

        return violations
    }

    private fun validateActiveExecutionSchedule(input: Input, violations: MutableList<String>) {
        if (input.activeExecutionsScheduleId.isBlank()) {
            violations += "prod/live profile must configure a non-blank active execution Temporal schedule id " +
                "(akra.temporal.schedules.active-executions.schedule-id)"
        }
        if (input.activeExecutionsScheduleRunIdPrefix.isBlank()) {
            violations += "prod/live profile must configure a non-blank active execution run id prefix " +
                "(akra.temporal.schedules.active-executions.execution-run-id-prefix)"
        }

        val scheduleTime = parseScheduleTime(
            hour = input.activeExecutionsScheduleHour,
            minute = input.activeExecutionsScheduleMinute,
            second = input.activeExecutionsScheduleSecond,
            violations = violations,
        )
        val scheduleZone = parseZoneId(input.activeExecutionsScheduleTimeZone)
        if (scheduleZone == null) {
            violations += "prod/live profile has invalid active execution schedule time-zone " +
                "(akra.temporal.schedules.active-executions.time-zone=${input.activeExecutionsScheduleTimeZone})"
        }

        val calendarZone = parseZoneId(input.usTradingCalendarZoneId)
        if (scheduleZone != null && calendarZone != null && scheduleZone != calendarZone) {
            violations += "prod/live profile active execution schedule time-zone must match US trading calendar zone " +
                "(akra.temporal.schedules.active-executions.time-zone=${input.activeExecutionsScheduleTimeZone}, " +
                "akra.trading-calendar.us.zone-id=${input.usTradingCalendarZoneId})"
        }

        val regularOpen = parseLocalTime(input.usTradingCalendarRegularOpen)
        val regularClose = parseLocalTime(input.usTradingCalendarRegularClose)
        if (scheduleTime != null && regularOpen != null && scheduleTime.isBefore(regularOpen)) {
            violations += "prod/live profile active execution schedule time must not be before US regular-open " +
                "(akra.temporal.schedules.active-executions=${formatScheduleTime(input)}, " +
                "akra.trading-calendar.us.regular-open=${input.usTradingCalendarRegularOpen})"
        }
        if (scheduleTime != null && regularClose != null && !scheduleTime.isBefore(regularClose)) {
            violations += "prod/live profile active execution schedule time must be before US regular-close " +
                "(akra.temporal.schedules.active-executions=${formatScheduleTime(input)}, " +
                "akra.trading-calendar.us.regular-close=${input.usTradingCalendarRegularClose})"
        }
    }

    private fun parseScheduleTime(
        hour: Int,
        minute: Int,
        second: Int,
        violations: MutableList<String>,
    ): LocalTime? {
        if (hour !in 0..23) {
            violations += "prod/live profile has invalid active execution schedule hour " +
                "(akra.temporal.schedules.active-executions.hour=$hour)"
        }
        if (minute !in 0..59) {
            violations += "prod/live profile has invalid active execution schedule minute " +
                "(akra.temporal.schedules.active-executions.minute=$minute)"
        }
        if (second !in 0..59) {
            violations += "prod/live profile has invalid active execution schedule second " +
                "(akra.temporal.schedules.active-executions.second=$second)"
        }
        return runCatching { LocalTime.of(hour, minute, second) }.getOrNull()
    }

    private fun validateUsTradingCalendar(input: Input, violations: MutableList<String>) {
        if (parseZoneId(input.usTradingCalendarZoneId) == null) {
            violations += "prod/live profile has invalid US trading calendar zone " +
                "(akra.trading-calendar.us.zone-id=${input.usTradingCalendarZoneId})"
        }

        val regularOpen = parseLocalTime(input.usTradingCalendarRegularOpen)
        if (regularOpen == null) {
            violations += "prod/live profile has invalid US trading calendar regular-open " +
                "(akra.trading-calendar.us.regular-open=${input.usTradingCalendarRegularOpen})"
        }

        val regularClose = parseLocalTime(input.usTradingCalendarRegularClose)
        if (regularClose == null) {
            violations += "prod/live profile has invalid US trading calendar regular-close " +
                "(akra.trading-calendar.us.regular-close=${input.usTradingCalendarRegularClose})"
        }

        if (regularOpen != null && regularClose != null && !regularOpen.isBefore(regularClose)) {
            violations += "prod/live profile US trading calendar regular-open must be before regular-close " +
                "(akra.trading-calendar.us.regular-open=${input.usTradingCalendarRegularOpen}, " +
                "akra.trading-calendar.us.regular-close=${input.usTradingCalendarRegularClose})"
        }

        input.usTradingCalendarHolidays.forEach { value ->
            if (parseLocalDate(value) == null) {
                violations += "prod/live profile has invalid US trading calendar holiday date " +
                    "(akra.trading-calendar.us.holidays[]=$value)"
            }
        }

        input.usTradingCalendarEarlyCloseDays.forEach { value ->
            if (parseLocalDate(value) == null) {
                violations += "prod/live profile has invalid US trading calendar early-close date " +
                    "(akra.trading-calendar.us.early-close-days[]=$value)"
            }
        }

        input.usTradingCalendarEarlyCloseTime
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { value ->
                val earlyCloseTime = parseLocalTime(value)
                if (earlyCloseTime == null) {
                    violations += "prod/live profile has invalid US trading calendar early-close time " +
                        "(akra.trading-calendar.us.early-close-time=$value)"
                } else {
                    validateEarlyCloseTime(
                        property = "akra.trading-calendar.us.early-close-time",
                        value = value,
                        earlyCloseTime = earlyCloseTime,
                        regularOpen = regularOpen,
                        regularClose = regularClose,
                        violations = violations,
                    )
                }
            }

        input.usTradingCalendarEarlyCloseTimes.forEach { (date, time) ->
            if (parseLocalDate(date) == null) {
                violations += "prod/live profile has invalid US trading calendar early-close override date " +
                    "(akra.trading-calendar.us.early-close-times[$date])"
            }

            val earlyCloseTime = parseLocalTime(time)
            if (earlyCloseTime == null) {
                violations += "prod/live profile has invalid US trading calendar early-close override time " +
                    "(akra.trading-calendar.us.early-close-times[$date]=$time)"
            } else {
                validateEarlyCloseTime(
                    property = "akra.trading-calendar.us.early-close-times[$date]",
                    value = time,
                    earlyCloseTime = earlyCloseTime,
                    regularOpen = regularOpen,
                    regularClose = regularClose,
                    violations = violations,
                )
            }
        }
    }

    private fun validateEarlyCloseTime(
        property: String,
        value: String,
        earlyCloseTime: LocalTime,
        regularOpen: LocalTime?,
        regularClose: LocalTime?,
        violations: MutableList<String>,
    ) {
        if (
            regularOpen != null &&
            regularClose != null &&
            (!earlyCloseTime.isAfter(regularOpen) || !earlyCloseTime.isBefore(regularClose))
        ) {
            violations += "prod/live profile US trading calendar early-close time must be after regular-open " +
                "and before regular-close ($property=$value, " +
                "akra.trading-calendar.us.regular-open=$regularOpen, " +
                "akra.trading-calendar.us.regular-close=$regularClose)"
        }
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

    private fun formatScheduleTime(input: Input): String {
        return "${input.activeExecutionsScheduleHour}:" +
            "${input.activeExecutionsScheduleMinute}:" +
            input.activeExecutionsScheduleSecond
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
}

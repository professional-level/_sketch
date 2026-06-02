package com.example.strategyexecutionservice.config.runtime

import com.example.strategyexecutionservice.config.calendar.TradingCalendarProperties
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
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
                usTradingCalendarEnabled = usCalendar.enabled,
                defaultUsEquityCalendarEnabled = usCalendar.defaultUsEquityCalendarEnabled,
                usTradingCalendarZoneId = usCalendar.zoneId,
                usTradingCalendarRegularOpen = usCalendar.regularOpen,
                usTradingCalendarRegularClose = usCalendar.regularClose,
                usTradingCalendarHolidays = usCalendar.holidays,
                usTradingCalendarEarlyCloseDays = usCalendar.earlyCloseDays,
                usTradingCalendarEarlyCloseTime = usCalendar.earlyCloseTime,
                usTradingCalendarEarlyCloseTimes = usCalendar.earlyCloseTimes.toMap(),
                allowLocalTemporalTargetInProduction = properties.allowLocalTemporalTargetInProduction,
                allowLocalMarketDataEndpointInProduction = properties.allowLocalMarketDataEndpointInProduction,
                allowMockOrderIntentInProduction = properties.allowMockOrderIntentInProduction,
                allowDisabledTradingCalendarInProduction = properties.allowDisabledTradingCalendarInProduction,
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
        val usTradingCalendarEnabled: Boolean,
        val defaultUsEquityCalendarEnabled: Boolean,
        val usTradingCalendarZoneId: String,
        val usTradingCalendarRegularOpen: String,
        val usTradingCalendarRegularClose: String,
        val usTradingCalendarHolidays: List<String>,
        val usTradingCalendarEarlyCloseDays: List<String>,
        val usTradingCalendarEarlyCloseTime: String?,
        val usTradingCalendarEarlyCloseTimes: Map<String, String>,
        val allowLocalTemporalTargetInProduction: Boolean,
        val allowLocalMarketDataEndpointInProduction: Boolean,
        val allowMockOrderIntentInProduction: Boolean,
        val allowDisabledTradingCalendarInProduction: Boolean,
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

        return violations
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

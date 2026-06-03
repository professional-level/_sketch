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
                hibernateDdlAuto = "update",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks blank temporal target in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = " ",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("non-blank Temporal target"))
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
    fun `blocks host docker temporal target in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "host.docker.internal:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("local Temporal target"))
    }

    @Test
    fun `blocks blank market data endpoint in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("live"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = " ",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("non-blank market-data KIS wrapper endpoint"))
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
    fun `blocks host docker market data endpoint in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("live"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "http://host.docker.internal:8079",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("local market-data KIS wrapper endpoint"))
    }

    @Test
    fun `blocks Hibernate automatic DDL in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("production"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                hibernateDdlAuto = "create-drop",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("Hibernate automatic DDL"))
    }

    @Test
    fun `allows schema validation DDL mode in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                hibernateDdlAuto = "validate",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks application secret property source in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                applicationSecretPropertySources = listOf("class path resource [application-secret.properties]"),
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("application-secret.properties"))
    }

    @Test
    fun `allows explicitly waived application secret property source in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                applicationSecretPropertySources = listOf("class path resource [application-secret.properties]"),
                allowApplicationSecretPropertySourceInProduction = true,
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `blocks non live default order intent environment in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                defaultOrderIntentTradingEnvironment = "PAPER",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("default order intents to LIVE"))
    }

    @Test
    fun `blocks malformed active execution temporal schedule in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                activeExecutionsScheduleId = " ",
                activeExecutionsScheduleHour = 24,
                activeExecutionsScheduleMinute = 60,
                activeExecutionsScheduleSecond = -1,
                activeExecutionsScheduleTimeZone = "Mars/Base",
                activeExecutionsScheduleRunIdPrefix = "",
            ),
        )

        assertEquals(6, violations.size)
        assertTrue(violations.any { it.contains("Temporal schedule id") })
        assertTrue(violations.any { it.contains("run id prefix") })
        assertTrue(violations.any { it.contains("schedule hour") })
        assertTrue(violations.any { it.contains("schedule minute") })
        assertTrue(violations.any { it.contains("schedule second") })
        assertTrue(violations.any { it.contains("schedule time-zone") })
    }

    @Test
    fun `blocks active execution schedule timezone mismatch in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                activeExecutionsScheduleTimeZone = "Asia/Seoul",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("must match US trading calendar zone"))
    }

    @Test
    fun `blocks active execution schedule outside regular session in production profile`() {
        val beforeOpen = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                activeExecutionsScheduleHour = 8,
                activeExecutionsScheduleMinute = 59,
            ),
        )
        val afterClose = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                activeExecutionsScheduleHour = 16,
                activeExecutionsScheduleMinute = 0,
            ),
        )

        assertEquals(1, beforeOpen.size)
        assertTrue(beforeOpen.single().contains("must not be before US regular-open"))
        assertEquals(1, afterClose.size)
        assertTrue(afterClose.single().contains("must be before US regular-close"))
    }

    @Test
    fun `blocks disabled us trading calendar in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                usTradingCalendarEnabled = false,
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("disable US trading calendar"))
    }

    @Test
    fun `blocks disabled default us equity calendar in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                defaultUsEquityCalendarEnabled = false,
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("default US equity calendar"))
    }

    @Test
    fun `blocks invalid us trading calendar zone in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                usTradingCalendarZoneId = "Mars/Base",
            ),
        )

        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("invalid US trading calendar zone"))
    }

    @Test
    fun `blocks invalid us trading calendar regular session in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                usTradingCalendarRegularOpen = "16:00",
                usTradingCalendarRegularClose = "09:30",
            ),
        )

        assertEquals(3, violations.size)
        assertTrue(violations.any { it.contains("regular-open must be before regular-close") })
        assertTrue(violations.any { it.contains("must not be before US regular-open") })
        assertTrue(violations.any { it.contains("must be before US regular-close") })
    }

    @Test
    fun `blocks malformed us trading calendar dates in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                usTradingCalendarHolidays = listOf("2026-13-01"),
                usTradingCalendarEarlyCloseDays = listOf("not-a-date"),
            ),
        )

        assertEquals(2, violations.size)
        assertTrue(violations.any { it.contains("invalid US trading calendar holiday date") })
        assertTrue(violations.any { it.contains("invalid US trading calendar early-close date") })
    }

    @Test
    fun `blocks malformed us trading calendar early close times in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                usTradingCalendarEarlyCloseTime = "market-close",
                usTradingCalendarEarlyCloseTimes = mapOf("2026-11-27" to "noon"),
            ),
        )

        assertEquals(2, violations.size)
        assertTrue(violations.any { it.contains("invalid US trading calendar early-close time") })
        assertTrue(violations.any { it.contains("invalid US trading calendar early-close override time") })
    }

    @Test
    fun `blocks us trading calendar early close outside regular session in production profile`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "temporal.example.com:7233",
                marketDataBaseUrl = "https://broker-wrapper.example.com",
                usTradingCalendarEarlyCloseTime = "08:00",
                usTradingCalendarEarlyCloseTimes = mapOf("2026-11-27" to "16:00"),
            ),
        )

        assertEquals(2, violations.size)
        assertTrue(violations.all { it.contains("after regular-open and before regular-close") })
    }

    @Test
    fun `allows explicitly waived production checks`() {
        val violations = StrategyExecutionRuntimeSafetyRules.validate(
            input(
                activeProfiles = listOf("prod"),
                temporalTarget = "127.0.0.1:7233",
                marketDataBaseUrl = "http://localhost:8079",
                hibernateDdlAuto = "validate",
                defaultOrderIntentTradingEnvironment = "MOCK",
                usTradingCalendarEnabled = false,
                usTradingCalendarZoneId = "invalid",
                usTradingCalendarRegularOpen = "invalid",
                usTradingCalendarRegularClose = "invalid",
                allowLocalTemporalTargetInProduction = true,
                allowLocalMarketDataEndpointInProduction = true,
                allowMockOrderIntentInProduction = true,
                allowDisabledTradingCalendarInProduction = true,
            ),
        )

        assertTrue(violations.isEmpty())
    }

    private fun input(
        activeProfiles: List<String>,
        temporalTarget: String,
        marketDataBaseUrl: String,
        hibernateDdlAuto: String? = "validate",
        defaultOrderIntentTradingEnvironment: String = "LIVE",
        activeExecutionsScheduleEnabled: Boolean = true,
        activeExecutionsScheduleId: String = "strategy-execution-active-executions-daily",
        activeExecutionsScheduleHour: Int = 9,
        activeExecutionsScheduleMinute: Int = 30,
        activeExecutionsScheduleSecond: Int = 0,
        activeExecutionsScheduleTimeZone: String = "America/New_York",
        activeExecutionsScheduleRunIdPrefix: String = "ACTIVE_STRATEGIES_DAILY",
        usTradingCalendarEnabled: Boolean = true,
        defaultUsEquityCalendarEnabled: Boolean = true,
        usTradingCalendarZoneId: String = "America/New_York",
        usTradingCalendarRegularOpen: String = "09:30",
        usTradingCalendarRegularClose: String = "16:00",
        usTradingCalendarHolidays: List<String> = emptyList(),
        usTradingCalendarEarlyCloseDays: List<String> = emptyList(),
        usTradingCalendarEarlyCloseTime: String? = null,
        usTradingCalendarEarlyCloseTimes: Map<String, String> = emptyMap(),
        applicationSecretPropertySources: List<String> = emptyList(),
        allowLocalTemporalTargetInProduction: Boolean = false,
        allowLocalMarketDataEndpointInProduction: Boolean = false,
        allowMockOrderIntentInProduction: Boolean = false,
        allowDisabledTradingCalendarInProduction: Boolean = false,
        allowApplicationSecretPropertySourceInProduction: Boolean = false,
    ) = StrategyExecutionRuntimeSafetyRules.Input(
        activeProfiles = activeProfiles,
        enabled = true,
        productionProfiles = listOf("prod", "production", "live"),
        temporalEnabled = true,
        temporalTarget = temporalTarget,
        marketDataBaseUrl = marketDataBaseUrl,
        hibernateDdlAuto = hibernateDdlAuto,
        defaultOrderIntentTradingEnvironment = defaultOrderIntentTradingEnvironment,
        activeExecutionsScheduleEnabled = activeExecutionsScheduleEnabled,
        activeExecutionsScheduleId = activeExecutionsScheduleId,
        activeExecutionsScheduleHour = activeExecutionsScheduleHour,
        activeExecutionsScheduleMinute = activeExecutionsScheduleMinute,
        activeExecutionsScheduleSecond = activeExecutionsScheduleSecond,
        activeExecutionsScheduleTimeZone = activeExecutionsScheduleTimeZone,
        activeExecutionsScheduleRunIdPrefix = activeExecutionsScheduleRunIdPrefix,
        usTradingCalendarEnabled = usTradingCalendarEnabled,
        defaultUsEquityCalendarEnabled = defaultUsEquityCalendarEnabled,
        usTradingCalendarZoneId = usTradingCalendarZoneId,
        usTradingCalendarRegularOpen = usTradingCalendarRegularOpen,
        usTradingCalendarRegularClose = usTradingCalendarRegularClose,
        usTradingCalendarHolidays = usTradingCalendarHolidays,
        usTradingCalendarEarlyCloseDays = usTradingCalendarEarlyCloseDays,
        usTradingCalendarEarlyCloseTime = usTradingCalendarEarlyCloseTime,
        usTradingCalendarEarlyCloseTimes = usTradingCalendarEarlyCloseTimes,
        applicationSecretPropertySources = applicationSecretPropertySources,
        allowLocalTemporalTargetInProduction = allowLocalTemporalTargetInProduction,
        allowLocalMarketDataEndpointInProduction = allowLocalMarketDataEndpointInProduction,
        allowMockOrderIntentInProduction = allowMockOrderIntentInProduction,
        allowDisabledTradingCalendarInProduction = allowDisabledTradingCalendarInProduction,
        allowApplicationSecretPropertySourceInProduction = allowApplicationSecretPropertySourceInProduction,
    )
}

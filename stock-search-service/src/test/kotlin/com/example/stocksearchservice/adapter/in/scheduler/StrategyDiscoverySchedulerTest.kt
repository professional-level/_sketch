package com.example.stocksearchservice.adapter.`in`.scheduler

import com.example.stocksearchservice.application.port.`in`.RequestStrategyDiscoveryRunCommand
import com.example.stocksearchservice.application.port.`in`.RequestStrategyDiscoveryRunUseCase
import com.example.stocksearchservice.application.port.`in`.StrategyDiscoveryRunResult
import com.example.stocksearchservice.application.port.`in`.StrategyDiscoveryType
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.springframework.scheduling.annotation.Scheduled

class StrategyDiscoverySchedulerTest {

    @Test
    fun `scheduler cron runs every day so us market calendar gate controls execution`() {
        val scheduled = checkNotNull(
            StrategyDiscoveryScheduler::class.java.declaredMethods
                .first { it.name == "requestStrategyDiscoveryRun" }
                .getAnnotation(Scheduled::class.java),
        )

        assertEquals("0 */1 * * * *", scheduled.cron)
    }

    @Test
    fun `scheduler skips discovery request when gate is closed`() = runBlocking {
        val useCase = CapturingRequestStrategyDiscoveryRunUseCase()
        val scheduler = StrategyDiscoveryScheduler(
            requestStrategyDiscoveryRunUseCase = useCase,
            scheduleGate = StrategyDiscoveryScheduleGate { false },
        )

        scheduler.requestStrategyDiscoveryRun()

        assertEquals(emptyList(), useCase.commands)
    }

    @Test
    fun `scheduler requests discovery when gate is open`() = runBlocking {
        val useCase = CapturingRequestStrategyDiscoveryRunUseCase()
        val scheduler = StrategyDiscoveryScheduler(
            requestStrategyDiscoveryRunUseCase = useCase,
            scheduleGate = StrategyDiscoveryScheduleGate { true },
        )

        scheduler.requestStrategyDiscoveryRun()

        val command = useCase.commands.single()
        assertEquals(StrategyDiscoveryType.FINAL_PRICE_BATING_V1, command.strategyType)
    }

    @Test
    fun `us equity gate blocks default market holiday`() {
        val gate = UsEquityStrategyDiscoveryScheduleGate()

        val shouldRequest = gate.shouldRequestDiscoveryAt(
            ZonedDateTime.parse("2026-07-03T10:00:00-04:00[America/New_York]"),
        )

        assertFalse(shouldRequest)
    }

    @Test
    fun `us equity gate allows regular trading day`() {
        val gate = UsEquityStrategyDiscoveryScheduleGate()

        val shouldRequest = gate.shouldRequestDiscoveryAt(
            ZonedDateTime.parse("2026-07-06T10:00:00-04:00[America/New_York]"),
        )

        assertTrue(shouldRequest)
    }

    private class CapturingRequestStrategyDiscoveryRunUseCase : RequestStrategyDiscoveryRunUseCase {
        val commands: MutableList<RequestStrategyDiscoveryRunCommand> = mutableListOf()

        override suspend fun execute(command: RequestStrategyDiscoveryRunCommand): StrategyDiscoveryRunResult {
            commands += command
            return StrategyDiscoveryRunResult(
                strategyType = command.strategyType,
                requestedAt = command.requestedAt,
                discoveredCount = 0,
            )
        }
    }
}

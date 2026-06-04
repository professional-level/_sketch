package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneCommand
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneStatus
import com.example.strategyexecutionservice.application.port.out.LaorOrderMilestoneSignal
import com.example.strategyexecutionservice.application.port.out.LaorWorkflowSignalPort
import com.example.strategyexecutionservice.application.port.out.LaorWorkflowSignalResult
import com.example.strategyexecutionservice.application.port.out.LaorWorkflowSignalStatus
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionMilestoneEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionMilestoneEventRecord
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SignalLaorOrderMilestoneServiceTest {

    @Test
    fun `records milestone and signals workflow`() = runBlocking {
        val milestoneEventPort = FakeStrategyExecutionMilestoneEventPort()
        val workflowSignalPort = FakeLaorWorkflowSignalPort()
        val service = SignalLaorOrderMilestoneService(milestoneEventPort, workflowSignalPort)

        val result = service.execute(command())

        assertEquals(SignalLaorOrderMilestoneStatus.SIGNALED, result.status)
        assertEquals("milestone-1", milestoneEventPort.saved.single().eventId)
        with(workflowSignalPort.signals.single()) {
            assertEquals("laor-v4:TQQQ", workflowId)
            assertEquals("ENTRY_BUY_FILLED", milestoneType)
            assertEquals(OrderSide.BUY, side)
            assertEquals(listOf("fill-1"), sourceEventIds)
        }
    }

    @Test
    fun `skips workflow signal for duplicate milestone`() = runBlocking {
        val milestoneEventPort = FakeStrategyExecutionMilestoneEventPort(duplicateEventIds = setOf("milestone-1"))
        val workflowSignalPort = FakeLaorWorkflowSignalPort()
        val service = SignalLaorOrderMilestoneService(milestoneEventPort, workflowSignalPort)

        val result = service.execute(command())

        assertEquals(SignalLaorOrderMilestoneStatus.SKIPPED_DUPLICATE, result.status)
        assertEquals(emptyList(), workflowSignalPort.signals)
    }

    private fun command(): SignalLaorOrderMilestoneCommand {
        return SignalLaorOrderMilestoneCommand(
            eventId = "milestone-1",
            milestoneType = "ENTRY_BUY_FILLED",
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-1",
            brokerOrderId = "broker-1",
            orderTag = "FIRST_BUY",
            side = OrderSide.BUY,
            filledQuantity = 10,
            averageFilledPrice = 100.5,
            occurredAt = ZonedDateTime.parse("2026-06-04T09:35:00-04:00"),
            sourceEventIds = listOf("fill-1"),
            idempotencyKey = "laor-v4:TQQQ:intent-1:ENTRY_BUY_FILLED:fill-1",
        )
    }

    private class FakeStrategyExecutionMilestoneEventPort(
        private val duplicateEventIds: Set<String> = emptySet(),
    ) : StrategyExecutionMilestoneEventPort {
        val saved: MutableList<StrategyExecutionMilestoneEventRecord> = mutableListOf()

        override suspend fun tryRecord(event: StrategyExecutionMilestoneEventRecord): Boolean {
            if (event.eventId in duplicateEventIds) return false

            saved += event
            return true
        }
    }

    private class FakeLaorWorkflowSignalPort : LaorWorkflowSignalPort {
        val signals: MutableList<LaorOrderMilestoneSignal> = mutableListOf()

        override suspend fun signal(signal: LaorOrderMilestoneSignal): LaorWorkflowSignalResult {
            signals += signal
            return LaorWorkflowSignalResult(LaorWorkflowSignalStatus.SIGNALED)
        }
    }
}

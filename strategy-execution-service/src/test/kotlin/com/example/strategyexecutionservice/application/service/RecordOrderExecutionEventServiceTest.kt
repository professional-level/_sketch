package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventCommand
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventStatus
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventRecord
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventType
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordOrderExecutionEventServiceTest {

    @Test
    fun `records submitted order event`() = runBlocking {
        val orderEventPort = FakeStrategyExecutionOrderEventPort()
        val service = RecordOrderExecutionEventService(orderEventPort)

        val result = service.execute(
            RecordOrderExecutionEventCommand.Submitted(
                eventId = "submitted-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                submittedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(RecordOrderExecutionEventStatus.RECORDED, result.status)
        with(orderEventPort.saved.single()) {
            assertEquals(StrategyExecutionOrderEventType.SUBMITTED, type)
            assertEquals("broker-1", brokerOrderId)
        }
    }

    @Test
    fun `records rejected order event with reason`() = runBlocking {
        val orderEventPort = FakeStrategyExecutionOrderEventPort()
        val service = RecordOrderExecutionEventService(orderEventPort)

        service.execute(
            RecordOrderExecutionEventCommand.Rejected(
                eventId = "rejected-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = null,
                reason = "broker rejected",
                rejectedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        with(orderEventPort.saved.single()) {
            assertEquals(StrategyExecutionOrderEventType.REJECTED, type)
            assertEquals("broker rejected", reason)
        }
    }

    @Test
    fun `records cancelled order event with reason`() = runBlocking {
        val orderEventPort = FakeStrategyExecutionOrderEventPort()
        val service = RecordOrderExecutionEventService(orderEventPort)

        service.execute(
            RecordOrderExecutionEventCommand.Cancelled(
                eventId = "cancelled-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                reason = "LOC expired",
                cancelledAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        with(orderEventPort.saved.single()) {
            assertEquals(StrategyExecutionOrderEventType.CANCELLED, type)
            assertEquals("LOC expired", reason)
            assertEquals("broker-1", brokerOrderId)
        }
    }

    @Test
    fun `skips duplicate submitted order event`() = runBlocking {
        val orderEventPort = FakeStrategyExecutionOrderEventPort(duplicateEventIds = setOf("submitted-1"))
        val service = RecordOrderExecutionEventService(orderEventPort)

        val result = service.execute(
            RecordOrderExecutionEventCommand.Submitted(
                eventId = "submitted-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                submittedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(RecordOrderExecutionEventStatus.SKIPPED_DUPLICATE, result.status)
        assertEquals(emptyList(), orderEventPort.saved)
    }

    private class FakeStrategyExecutionOrderEventPort(
        private val duplicateEventIds: Set<String> = emptySet(),
    ) : StrategyExecutionOrderEventPort {
        val saved: MutableList<StrategyExecutionOrderEventRecord> = mutableListOf()

        override suspend fun tryRecord(event: StrategyExecutionOrderEventRecord): Boolean {
            if (event.eventId in duplicateEventIds) return false

            saved += event
            return true
        }
    }
}

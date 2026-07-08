package com.example.strategyexecutionservice.adapter.`in`.event

import Event
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillCommand
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillResult
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillStatus
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillUseCase
import com.example.strategyexecutionservice.application.port.`in`.OrderFillKind
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyCommand
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyResult
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyStatus
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyUseCase
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventCommand
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventResult
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventUseCase
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneCommand
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneResult
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneStatus
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneUseCase
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionUseCase
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.google.protobuf.Timestamp
import common.Topic.LAOR_ORDER_ANOMALY_DETECTED
import common.Topic.LAOR_ORDER_MILESTONE_DETECTED
import common.Topic.ORDER_FILLED
import common.observability.TraceContext
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StrategyExecutionEventListenerAdapterTest {

    @Test
    fun `restores trace context from Kafka headers while handling order fill`() = runBlocking {
        val fillUseCase = CapturingApplyOrderFillUseCase()
        val adapter = StrategyExecutionEventListenerAdapter(
            startStrategyExecutionUseCase = NoopStartStrategyExecutionUseCase(),
            applyOrderFillUseCase = fillUseCase,
            recordOrderExecutionEventUseCase = NoopRecordOrderExecutionEventUseCase(),
            signalLaorOrderMilestoneUseCase = NoopSignalLaorOrderMilestoneUseCase(),
            recordLaorOrderAnomalyUseCase = NoopRecordLaorOrderAnomalyUseCase(),
        )
        val record = orderFilledRecord()
            .withHeader(TraceContext.TRACEPARENT_KEY, TRACE_PARENT)
            .withHeader(TraceContext.TRACE_ID_KEY, TRACE_ID)
            .withHeader(TraceContext.SPAN_ID_KEY, SPAN_ID)

        MDC.put(TraceContext.TRACE_ID_KEY, "previous-trace")
        try {
            adapter.orderFills(record)
        } finally {
            assertEquals("previous-trace", MDC.get(TraceContext.TRACE_ID_KEY))
            assertNull(MDC.get(TraceContext.SPAN_ID_KEY))
            assertNull(MDC.get(TraceContext.TRACEPARENT_KEY))
            MDC.clear()
        }

        assertEquals(Triple(TRACE_ID, SPAN_ID, TRACE_PARENT), fillUseCase.observedTrace)
        with(fillUseCase.commands.single()) {
            assertEquals("fill-1", eventId)
            assertEquals("laor-v4:TQQQ", strategyExecutionId)
            assertEquals("intent-1", orderIntentId)
            assertEquals("broker-1", brokerOrderId)
            assertEquals(OrderSide.BUY, side)
            assertEquals(OrderFillKind.FILLED, fillKind)
            assertEquals(100.5, filledPrice)
            assertEquals(2L, filledQuantity)
            assertEquals("FIRST_BUY", orderTag)
            assertEquals("fill-key-1", idempotencyKey)
            assertEquals(Instant.ofEpochSecond(FILLED_AT_EPOCH_SECONDS), filledAt.toInstant())
        }
    }

    @Test
    fun `maps laor milestone event to workflow signal command`() = runBlocking {
        val signalUseCase = CapturingSignalLaorOrderMilestoneUseCase()
        val adapter = StrategyExecutionEventListenerAdapter(
            startStrategyExecutionUseCase = NoopStartStrategyExecutionUseCase(),
            applyOrderFillUseCase = NoopApplyOrderFillUseCase(),
            recordOrderExecutionEventUseCase = NoopRecordOrderExecutionEventUseCase(),
            signalLaorOrderMilestoneUseCase = signalUseCase,
            recordLaorOrderAnomalyUseCase = NoopRecordLaorOrderAnomalyUseCase(),
        )
        val record = laorMilestoneRecord()

        adapter.laorOrderMilestones(record)

        with(signalUseCase.commands.single()) {
            assertEquals("milestone-1", eventId)
            assertEquals("ENTRY_BUY_FILLED", milestoneType)
            assertEquals("laor-v4:TQQQ", strategyExecutionId)
            assertEquals("intent-1", orderIntentId)
            assertEquals("broker-1", brokerOrderId)
            assertEquals("FIRST_BUY", orderTag)
            assertEquals(OrderSide.BUY, side)
            assertEquals(10L, filledQuantity)
            assertEquals(100.5, averageFilledPrice)
            assertEquals(listOf("submitted-1", "fill-1"), sourceEventIds)
            assertEquals("laor-v4:TQQQ:intent-1:ENTRY_BUY_FILLED:fill-1", idempotencyKey)
            assertEquals(Instant.ofEpochSecond(FILLED_AT_EPOCH_SECONDS), occurredAt.toInstant())
        }
    }

    @Test
    fun `maps laor anomaly event to record command`() = runBlocking {
        val anomalyUseCase = CapturingRecordLaorOrderAnomalyUseCase()
        val adapter = StrategyExecutionEventListenerAdapter(
            startStrategyExecutionUseCase = NoopStartStrategyExecutionUseCase(),
            applyOrderFillUseCase = NoopApplyOrderFillUseCase(),
            recordOrderExecutionEventUseCase = NoopRecordOrderExecutionEventUseCase(),
            signalLaorOrderMilestoneUseCase = NoopSignalLaorOrderMilestoneUseCase(),
            recordLaorOrderAnomalyUseCase = anomalyUseCase,
        )

        adapter.laorOrderAnomalies(laorAnomalyRecord())

        with(anomalyUseCase.commands.single()) {
            assertEquals("anomaly-1", eventId)
            assertEquals("FILL_BEFORE_SUBMIT", anomalyType)
            assertEquals("laor-v4:TQQQ", strategyExecutionId)
            assertEquals("intent-1", orderIntentId)
            assertEquals("broker-1", brokerOrderId)
            assertEquals("FIRST_BUY", orderTag)
            assertEquals(OrderSide.BUY, side)
            assertEquals("fill arrived before submitted event", reason)
            assertEquals(listOf("fill-1"), sourceEventIds)
            assertEquals("laor-v4:TQQQ:intent-1:FILL_BEFORE_SUBMIT:fill-1", idempotencyKey)
            assertEquals(Instant.ofEpochSecond(FILLED_AT_EPOCH_SECONDS), occurredAt.toInstant())
        }
    }

    private fun orderFilledRecord(): ConsumerRecord<String, ByteArray> {
        val event = Event.OrderFilled.newBuilder()
            .setEventId("fill-1")
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setOrderIntentId("intent-1")
            .setBrokerOrderId("broker-1")
            .setSide(Event.OrderIntentSide.ORDER_INTENT_BUY)
            .setFilledPrice(100.5)
            .setFilledQuantity(2L)
            .setOrderTag("FIRST_BUY")
            .setFilledAt(Timestamp.newBuilder().setSeconds(FILLED_AT_EPOCH_SECONDS).build())
            .setIdempotencyKey("fill-key-1")
            .build()
        return ConsumerRecord(ORDER_FILLED, 0, 0L, "laor-v4:TQQQ", event.toByteArray())
    }

    private fun laorMilestoneRecord(): ConsumerRecord<String, ByteArray> {
        val event = Event.LaorOrderMilestoneDetected.newBuilder()
            .setEventId("milestone-1")
            .setMilestoneType(Event.LaorOrderMilestoneType.ENTRY_BUY_FILLED)
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setOrderIntentId("intent-1")
            .setBrokerOrderId("broker-1")
            .setOrderTag("FIRST_BUY")
            .setSide(Event.OrderIntentSide.ORDER_INTENT_BUY)
            .setFilledQuantity(10L)
            .setAverageFilledPrice(100.5)
            .setOccurredAt(Timestamp.newBuilder().setSeconds(FILLED_AT_EPOCH_SECONDS).build())
            .addSourceEventIds("submitted-1")
            .addSourceEventIds("fill-1")
            .setIdempotencyKey("laor-v4:TQQQ:intent-1:ENTRY_BUY_FILLED:fill-1")
            .build()
        return ConsumerRecord(LAOR_ORDER_MILESTONE_DETECTED, 0, 0L, "laor-v4:TQQQ", event.toByteArray())
    }

    private fun laorAnomalyRecord(): ConsumerRecord<String, ByteArray> {
        val event = Event.LaorOrderAnomalyDetected.newBuilder()
            .setEventId("anomaly-1")
            .setAnomalyType(Event.LaorOrderAnomalyType.FILL_BEFORE_SUBMIT)
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setOrderIntentId("intent-1")
            .setBrokerOrderId("broker-1")
            .setOrderTag("FIRST_BUY")
            .setSide(Event.OrderIntentSide.ORDER_INTENT_BUY)
            .setReason("fill arrived before submitted event")
            .setOccurredAt(Timestamp.newBuilder().setSeconds(FILLED_AT_EPOCH_SECONDS).build())
            .addSourceEventIds("fill-1")
            .setIdempotencyKey("laor-v4:TQQQ:intent-1:FILL_BEFORE_SUBMIT:fill-1")
            .build()
        return ConsumerRecord(LAOR_ORDER_ANOMALY_DETECTED, 0, 0L, "laor-v4:TQQQ", event.toByteArray())
    }

    private fun ConsumerRecord<String, ByteArray>.withHeader(
        key: String,
        value: String,
    ): ConsumerRecord<String, ByteArray> {
        headers().add(key, value.toByteArray(StandardCharsets.UTF_8))
        return this
    }

    private class CapturingApplyOrderFillUseCase : ApplyOrderFillUseCase {
        val commands: MutableList<ApplyOrderFillCommand> = mutableListOf()
        var observedTrace: Triple<String?, String?, String?>? = null

        override suspend fun execute(command: ApplyOrderFillCommand): ApplyOrderFillResult {
            commands += command
            observedTrace = Triple(
                MDC.get(TraceContext.TRACE_ID_KEY),
                MDC.get(TraceContext.SPAN_ID_KEY),
                MDC.get(TraceContext.TRACEPARENT_KEY),
            )
            return ApplyOrderFillResult(command.strategyExecutionId, ApplyOrderFillStatus.APPLIED)
        }
    }

    private class CapturingSignalLaorOrderMilestoneUseCase : SignalLaorOrderMilestoneUseCase {
        val commands: MutableList<SignalLaorOrderMilestoneCommand> = mutableListOf()

        override suspend fun execute(
            command: SignalLaorOrderMilestoneCommand,
        ): SignalLaorOrderMilestoneResult {
            commands += command
            return SignalLaorOrderMilestoneResult(command.strategyExecutionId, SignalLaorOrderMilestoneStatus.SIGNALED)
        }
    }

    private class CapturingRecordLaorOrderAnomalyUseCase : RecordLaorOrderAnomalyUseCase {
        val commands: MutableList<RecordLaorOrderAnomalyCommand> = mutableListOf()

        override suspend fun execute(command: RecordLaorOrderAnomalyCommand): RecordLaorOrderAnomalyResult {
            commands += command
            return RecordLaorOrderAnomalyResult(command.strategyExecutionId, RecordLaorOrderAnomalyStatus.RECORDED)
        }
    }

    private class NoopApplyOrderFillUseCase : ApplyOrderFillUseCase {
        override suspend fun execute(command: ApplyOrderFillCommand): ApplyOrderFillResult {
            error("not used")
        }
    }

    private class NoopStartStrategyExecutionUseCase : StartStrategyExecutionUseCase {
        override suspend fun execute(command: StartStrategyExecutionCommand): StartStrategyExecutionResult {
            error("not used")
        }
    }

    private class NoopRecordOrderExecutionEventUseCase : RecordOrderExecutionEventUseCase {
        override suspend fun execute(command: RecordOrderExecutionEventCommand): RecordOrderExecutionEventResult {
            error("not used")
        }
    }

    private class NoopSignalLaorOrderMilestoneUseCase : SignalLaorOrderMilestoneUseCase {
        override suspend fun execute(command: SignalLaorOrderMilestoneCommand): SignalLaorOrderMilestoneResult {
            error("not used")
        }
    }

    private class NoopRecordLaorOrderAnomalyUseCase : RecordLaorOrderAnomalyUseCase {
        override suspend fun execute(command: RecordLaorOrderAnomalyCommand): RecordLaorOrderAnomalyResult {
            error("not used")
        }
    }

    private companion object {
        const val FILLED_AT_EPOCH_SECONDS = 1_754_092_800L
        const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        const val SPAN_ID = "00f067aa0ba902b7"
        const val TRACE_PARENT = "00-$TRACE_ID-$SPAN_ID-01"
    }
}

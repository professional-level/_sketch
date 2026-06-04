package com.example.streamprocessingservice.laor.adapter.out.kafka

import Event
import com.example.streamprocessingservice.laor.application.AnomalyEvent
import com.example.streamprocessingservice.laor.application.AnomalyType
import com.example.streamprocessingservice.laor.application.MilestoneEvent
import com.example.streamprocessingservice.laor.application.LifecycleEventEnvelope
import com.example.streamprocessingservice.laor.application.MilestoneType
import com.example.streamprocessingservice.laor.application.OrderSide
import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.ZonedDateTime

class OrderLifecycleEventSerializerTest {
    @Test
    fun `serializes milestone envelope to protobuf`() {
        val serializer = OrderLifecycleEventSerializer()
        val event = MilestoneEvent(
            eventId = "milestone-1",
            milestoneType = MilestoneType.ENTRY_BUY_FILLED,
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-1",
            brokerOrderId = "broker-1",
            orderTag = "FIRST_BUY",
            side = OrderSide.BUY,
            filledQuantity = 10,
            averageFilledPrice = 100.5,
            occurredAt = ZonedDateTime.parse("2026-06-04T09:30:00-04:00"),
            sourceEventIds = listOf("fill-1"),
            idempotencyKey = "laor-v4:TQQQ:intent-1:ENTRY_BUY_FILLED:fill-1",
        )

        val proto = Event.LaorOrderMilestoneDetected.parseFrom(
            serializer.serialize(LifecycleEventEnvelope(milestone = event)),
        )

        assertEquals("milestone-1", proto.eventId)
        assertEquals(Event.LaorOrderMilestoneType.ENTRY_BUY_FILLED, proto.milestoneType)
        assertEquals("laor-v4:TQQQ", proto.strategyExecutionId)
        assertEquals("intent-1", proto.orderIntentId)
        assertEquals(Event.OrderIntentSide.ORDER_INTENT_BUY, proto.side)
        assertEquals(10, proto.filledQuantity)
        assertEquals("fill-1", proto.sourceEventIdsList.single())
    }

    @Test
    fun `serializes anomaly envelope to protobuf`() {
        val serializer = OrderLifecycleEventSerializer()
        val event = AnomalyEvent(
            eventId = "anomaly-1",
            anomalyType = AnomalyType.FILL_BEFORE_SUBMIT,
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-1",
            brokerOrderId = "broker-1",
            orderTag = "FIRST_BUY",
            side = OrderSide.BUY,
            reason = "fill arrived before submitted event",
            occurredAt = ZonedDateTime.parse("2026-06-04T09:30:00-04:00"),
            sourceEventIds = listOf("fill-1"),
            idempotencyKey = "laor-v4:TQQQ:intent-1:FILL_BEFORE_SUBMIT:fill-1",
        )

        val proto = Event.LaorOrderAnomalyDetected.parseFrom(
            serializer.serialize(LifecycleEventEnvelope(anomaly = event)),
        )

        assertEquals("anomaly-1", proto.eventId)
        assertEquals(Event.LaorOrderAnomalyType.FILL_BEFORE_SUBMIT, proto.anomalyType)
        assertEquals("laor-v4:TQQQ", proto.strategyExecutionId)
        assertEquals("intent-1", proto.orderIntentId)
        assertEquals(Event.OrderIntentSide.ORDER_INTENT_BUY, proto.side)
        assertEquals("fill arrived before submitted event", proto.reason)
        assertEquals("fill-1", proto.sourceEventIdsList.single())
    }
}

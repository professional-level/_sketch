package com.example.streamprocessingservice.laor.adapter.out.kafka

import Event
import com.example.streamprocessingservice.laor.application.AnomalyEvent
import com.example.streamprocessingservice.laor.application.AnomalyType
import com.example.streamprocessingservice.laor.application.MilestoneEvent
import com.example.streamprocessingservice.laor.application.LifecycleEventEnvelope
import com.example.streamprocessingservice.laor.application.MilestoneType
import com.example.streamprocessingservice.laor.application.OrderSide
import common.proto.ProtoUtils.toProtobufTimestamp

class OrderLifecycleEventSerializer {
    fun serialize(envelope: LifecycleEventEnvelope): ByteArray {
        return envelope.milestone?.toProto()?.toByteArray()
            ?: checkNotNull(envelope.anomaly).toProto().toByteArray()
    }

    private fun MilestoneEvent.toProto(): Event.LaorOrderMilestoneDetected {
        return Event.LaorOrderMilestoneDetected.newBuilder()
            .setEventId(eventId)
            .setMilestoneType(milestoneType.toProto())
            .setStrategyExecutionId(strategyExecutionId)
            .setOrderIntentId(orderIntentId)
            .setBrokerOrderId(brokerOrderId.orEmpty())
            .setOrderTag(orderTag.orEmpty())
            .setSide(side.toProto())
            .setFilledQuantity(filledQuantity)
            .setAverageFilledPrice(averageFilledPrice ?: 0.0)
            .setOccurredAt(occurredAt.toProtobufTimestamp())
            .setMeta(
                Event.EventMeta.newBuilder()
                    .setOccurredAt(occurredAt.toProtobufTimestamp())
                    .setServiceName("stream-processing-service"),
            )
            .addAllSourceEventIds(sourceEventIds)
            .setIdempotencyKey(idempotencyKey)
            .build()
    }

    private fun AnomalyEvent.toProto(): Event.LaorOrderAnomalyDetected {
        return Event.LaorOrderAnomalyDetected.newBuilder()
            .setEventId(eventId)
            .setAnomalyType(anomalyType.toProto())
            .setStrategyExecutionId(strategyExecutionId)
            .setOrderIntentId(orderIntentId)
            .setBrokerOrderId(brokerOrderId.orEmpty())
            .setOrderTag(orderTag.orEmpty())
            .setSide(side.toProto())
            .setReason(reason)
            .setOccurredAt(occurredAt.toProtobufTimestamp())
            .setMeta(
                Event.EventMeta.newBuilder()
                    .setOccurredAt(occurredAt.toProtobufTimestamp())
                    .setServiceName("stream-processing-service"),
            )
            .addAllSourceEventIds(sourceEventIds)
            .setIdempotencyKey(idempotencyKey)
            .build()
    }

    private fun MilestoneType.toProto(): Event.LaorOrderMilestoneType {
        return when (this) {
            MilestoneType.ENTRY_BUY_SUBMITTED -> Event.LaorOrderMilestoneType.ENTRY_BUY_SUBMITTED
            MilestoneType.ENTRY_BUY_PARTIALLY_FILLED -> Event.LaorOrderMilestoneType.ENTRY_BUY_PARTIALLY_FILLED
            MilestoneType.ENTRY_BUY_FILLED -> Event.LaorOrderMilestoneType.ENTRY_BUY_FILLED
            MilestoneType.EXIT_SELL_SUBMITTED -> Event.LaorOrderMilestoneType.EXIT_SELL_SUBMITTED
            MilestoneType.EXIT_SELL_PARTIALLY_FILLED -> Event.LaorOrderMilestoneType.EXIT_SELL_PARTIALLY_FILLED
            MilestoneType.EXIT_SELL_FILLED -> Event.LaorOrderMilestoneType.EXIT_SELL_FILLED
            MilestoneType.ORDER_REJECTED -> Event.LaorOrderMilestoneType.ORDER_REJECTED
            MilestoneType.ORDER_CANCELLED -> Event.LaorOrderMilestoneType.ORDER_CANCELLED
            MilestoneType.ORDER_FILL_TIMEOUT_DETECTED ->
                Event.LaorOrderMilestoneType.ORDER_FILL_TIMEOUT_DETECTED
        }
    }

    private fun AnomalyType.toProto(): Event.LaorOrderAnomalyType {
        return when (this) {
            AnomalyType.FILL_BEFORE_SUBMIT -> Event.LaorOrderAnomalyType.FILL_BEFORE_SUBMIT
            AnomalyType.DUPLICATE_TERMINAL_EVENT -> Event.LaorOrderAnomalyType.DUPLICATE_TERMINAL_EVENT
            AnomalyType.FILLED_QUANTITY_EXCEEDS_EXPECTED ->
                Event.LaorOrderAnomalyType.FILLED_QUANTITY_EXCEEDS_EXPECTED
            AnomalyType.CONFLICTING_BROKER_ORDER_ID -> Event.LaorOrderAnomalyType.CONFLICTING_BROKER_ORDER_ID
            AnomalyType.UNKNOWN_ORDER_INTENT -> Event.LaorOrderAnomalyType.UNKNOWN_ORDER_INTENT
            AnomalyType.LATE_EVENT_AFTER_TERMINAL -> Event.LaorOrderAnomalyType.LATE_EVENT_AFTER_TERMINAL
        }
    }

    private fun OrderSide.toProto(): Event.OrderIntentSide {
        return when (this) {
            OrderSide.BUY -> Event.OrderIntentSide.ORDER_INTENT_BUY
            OrderSide.SELL -> Event.OrderIntentSide.ORDER_INTENT_SELL
            OrderSide.UNKNOWN -> Event.OrderIntentSide.ORDER_INTENT_SIDE_UNDEFINED
        }
    }
}

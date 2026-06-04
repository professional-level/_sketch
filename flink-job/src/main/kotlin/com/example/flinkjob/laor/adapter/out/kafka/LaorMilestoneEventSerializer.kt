package com.example.flinkjob.laor.adapter.out.kafka

import Event
import com.example.flinkjob.laor.application.LaorAnomalyEvent
import com.example.flinkjob.laor.application.LaorAnomalyType
import com.example.flinkjob.laor.application.LaorMilestoneEvent
import com.example.flinkjob.laor.application.LaorMilestoneEnvelope
import com.example.flinkjob.laor.application.LaorMilestoneType
import com.example.flinkjob.laor.application.LaorOrderSide
import common.proto.ProtoUtils.toProtobufTimestamp

class LaorMilestoneEventSerializer {
    fun serialize(envelope: LaorMilestoneEnvelope): ByteArray {
        return envelope.milestone?.toProto()?.toByteArray()
            ?: checkNotNull(envelope.anomaly).toProto().toByteArray()
    }

    private fun LaorMilestoneEvent.toProto(): Event.LaorOrderMilestoneDetected {
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
                    .setServiceName("flink-job"),
            )
            .addAllSourceEventIds(sourceEventIds)
            .setIdempotencyKey(idempotencyKey)
            .build()
    }

    private fun LaorAnomalyEvent.toProto(): Event.LaorOrderAnomalyDetected {
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
                    .setServiceName("flink-job"),
            )
            .addAllSourceEventIds(sourceEventIds)
            .setIdempotencyKey(idempotencyKey)
            .build()
    }

    private fun LaorMilestoneType.toProto(): Event.LaorOrderMilestoneType {
        return when (this) {
            LaorMilestoneType.ENTRY_BUY_SUBMITTED -> Event.LaorOrderMilestoneType.ENTRY_BUY_SUBMITTED
            LaorMilestoneType.ENTRY_BUY_PARTIALLY_FILLED -> Event.LaorOrderMilestoneType.ENTRY_BUY_PARTIALLY_FILLED
            LaorMilestoneType.ENTRY_BUY_FILLED -> Event.LaorOrderMilestoneType.ENTRY_BUY_FILLED
            LaorMilestoneType.EXIT_SELL_SUBMITTED -> Event.LaorOrderMilestoneType.EXIT_SELL_SUBMITTED
            LaorMilestoneType.EXIT_SELL_PARTIALLY_FILLED -> Event.LaorOrderMilestoneType.EXIT_SELL_PARTIALLY_FILLED
            LaorMilestoneType.EXIT_SELL_FILLED -> Event.LaorOrderMilestoneType.EXIT_SELL_FILLED
            LaorMilestoneType.ORDER_REJECTED -> Event.LaorOrderMilestoneType.ORDER_REJECTED
            LaorMilestoneType.ORDER_CANCELLED -> Event.LaorOrderMilestoneType.ORDER_CANCELLED
            LaorMilestoneType.ORDER_FILL_TIMEOUT_DETECTED ->
                Event.LaorOrderMilestoneType.ORDER_FILL_TIMEOUT_DETECTED
        }
    }

    private fun LaorAnomalyType.toProto(): Event.LaorOrderAnomalyType {
        return when (this) {
            LaorAnomalyType.FILL_BEFORE_SUBMIT -> Event.LaorOrderAnomalyType.FILL_BEFORE_SUBMIT
            LaorAnomalyType.DUPLICATE_TERMINAL_EVENT -> Event.LaorOrderAnomalyType.DUPLICATE_TERMINAL_EVENT
            LaorAnomalyType.FILLED_QUANTITY_EXCEEDS_EXPECTED ->
                Event.LaorOrderAnomalyType.FILLED_QUANTITY_EXCEEDS_EXPECTED
            LaorAnomalyType.CONFLICTING_BROKER_ORDER_ID -> Event.LaorOrderAnomalyType.CONFLICTING_BROKER_ORDER_ID
            LaorAnomalyType.UNKNOWN_ORDER_INTENT -> Event.LaorOrderAnomalyType.UNKNOWN_ORDER_INTENT
            LaorAnomalyType.LATE_EVENT_AFTER_TERMINAL -> Event.LaorOrderAnomalyType.LATE_EVENT_AFTER_TERMINAL
        }
    }

    private fun LaorOrderSide.toProto(): Event.OrderIntentSide {
        return when (this) {
            LaorOrderSide.BUY -> Event.OrderIntentSide.ORDER_INTENT_BUY
            LaorOrderSide.SELL -> Event.OrderIntentSide.ORDER_INTENT_SELL
            LaorOrderSide.UNKNOWN -> Event.OrderIntentSide.ORDER_INTENT_SIDE_UNDEFINED
        }
    }
}

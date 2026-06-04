package com.example.streamprocessingservice.laor.adapter.`in`.kafka

import Event
import com.example.streamprocessingservice.laor.application.OrderExecutionEvent
import com.example.streamprocessingservice.laor.application.OrderSide
import com.example.streamprocessingservice.laor.application.OrderExecutionEventType
import com.example.streamprocessingservice.laor.application.StrategyExecutionKind
import common.proto.ProtoUtils.toZonedDateTime
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import java.nio.charset.StandardCharsets
import java.util.UUID

abstract class OrderExecutionEventDeserializer : DeserializationSchema<OrderExecutionEvent> {
    override fun isEndOfStream(nextElement: OrderExecutionEvent): Boolean = false

    override fun getProducedType(): TypeInformation<OrderExecutionEvent> {
        return TypeInformation.of(OrderExecutionEvent::class.java)
    }
}

class OrderIntentCreatedEventDeserializer : OrderExecutionEventDeserializer() {
    override fun deserialize(message: ByteArray): OrderExecutionEvent {
        val event = Event.OrderIntentCreatedEvent.parseFrom(message)
        return OrderExecutionEvent(
            eventId = event.eventId.ifBlank {
                deterministicFallbackId(event.strategyExecutionId, event.orderTag, event.createdAt.seconds, event.createdAt.nanos)
            },
            type = OrderExecutionEventType.INTENT_CREATED,
            strategyExecutionId = event.strategyExecutionId,
            orderIntentId = event.eventId.ifBlank {
                deterministicFallbackId(event.strategyExecutionId, event.orderTag, event.createdAt.seconds, event.createdAt.nanos)
            },
            strategyKind = event.strategyType.toStrategyKind(),
            side = event.side.toDomainSide(),
            orderTag = event.orderTag,
            expectedQuantity = event.quantity.takeIf { it > 0 },
            occurredAt = event.createdAt.toZonedDateTime(),
        )
    }
}

class OrderSubmittedEventDeserializer : OrderExecutionEventDeserializer() {
    override fun deserialize(message: ByteArray): OrderExecutionEvent {
        val event = Event.OrderSubmitted.parseFrom(message)
        return OrderExecutionEvent(
            eventId = event.eventId,
            type = OrderExecutionEventType.SUBMITTED,
            strategyExecutionId = event.strategyExecutionId,
            orderIntentId = event.orderIntentId,
            brokerOrderId = event.brokerOrderId.ifBlank { null },
            side = event.side.toDomainSide(),
            orderTag = event.orderTag.ifBlank { null },
            expectedQuantity = event.quantity.takeIf { it > 0 },
            occurredAt = event.submittedAt.toZonedDateTime(),
        )
    }
}

class OrderPartiallyFilledEventDeserializer : OrderExecutionEventDeserializer() {
    override fun deserialize(message: ByteArray): OrderExecutionEvent {
        val event = Event.OrderPartiallyFilled.parseFrom(message)
        return OrderExecutionEvent(
            eventId = event.eventId,
            type = OrderExecutionEventType.PARTIALLY_FILLED,
            strategyExecutionId = event.strategyExecutionId,
            orderIntentId = event.orderIntentId,
            brokerOrderId = event.brokerOrderId.ifBlank { null },
            side = event.side.toDomainSide(),
            orderTag = event.orderTag,
            filledPrice = event.filledPrice,
            filledQuantity = event.filledQuantity,
            occurredAt = event.filledAt.toZonedDateTime(),
        )
    }
}

class OrderFilledEventDeserializer : OrderExecutionEventDeserializer() {
    override fun deserialize(message: ByteArray): OrderExecutionEvent {
        val event = Event.OrderFilled.parseFrom(message)
        return OrderExecutionEvent(
            eventId = event.eventId,
            type = OrderExecutionEventType.FILLED,
            strategyExecutionId = event.strategyExecutionId,
            orderIntentId = event.orderIntentId,
            brokerOrderId = event.brokerOrderId.ifBlank { null },
            side = event.side.toDomainSide(),
            orderTag = event.orderTag,
            filledPrice = event.filledPrice,
            filledQuantity = event.filledQuantity,
            occurredAt = event.filledAt.toZonedDateTime(),
        )
    }
}

class OrderRejectedEventDeserializer : OrderExecutionEventDeserializer() {
    override fun deserialize(message: ByteArray): OrderExecutionEvent {
        val event = Event.OrderRejected.parseFrom(message)
        return OrderExecutionEvent(
            eventId = event.eventId,
            type = OrderExecutionEventType.REJECTED,
            strategyExecutionId = event.strategyExecutionId,
            orderIntentId = event.orderIntentId,
            brokerOrderId = event.brokerOrderId.ifBlank { null },
            reason = event.reason,
            occurredAt = event.rejectedAt.toZonedDateTime(),
        )
    }
}

class OrderCancelledEventDeserializer : OrderExecutionEventDeserializer() {
    override fun deserialize(message: ByteArray): OrderExecutionEvent {
        val event = Event.OrderCancelled.parseFrom(message)
        return OrderExecutionEvent(
            eventId = event.eventId,
            type = OrderExecutionEventType.CANCELLED,
            strategyExecutionId = event.strategyExecutionId,
            orderIntentId = event.orderIntentId,
            brokerOrderId = event.brokerOrderId.ifBlank { null },
            reason = event.reason,
            occurredAt = event.cancelledAt.toZonedDateTime(),
        )
    }
}

private fun Event.OrderIntentSide.toDomainSide(): OrderSide {
    return when (this) {
        Event.OrderIntentSide.ORDER_INTENT_BUY -> OrderSide.BUY
        Event.OrderIntentSide.ORDER_INTENT_SELL -> OrderSide.SELL
        Event.OrderIntentSide.ORDER_INTENT_SIDE_UNDEFINED,
        Event.OrderIntentSide.UNRECOGNIZED -> OrderSide.UNKNOWN
    }
}

private fun Event.StrategyExecutionType.toStrategyKind(): StrategyExecutionKind {
    return when (this) {
        Event.StrategyExecutionType.LAOR_V4_STRATEGY -> StrategyExecutionKind.LAOR_V4
        Event.StrategyExecutionType.FINAL_PRICE_BATING_V1_STRATEGY -> StrategyExecutionKind.OTHER
        Event.StrategyExecutionType.STRATEGY_EXECUTION_TYPE_UNDEFINED,
        Event.StrategyExecutionType.UNRECOGNIZED -> StrategyExecutionKind.UNKNOWN
    }
}

private fun deterministicFallbackId(
    strategyExecutionId: String,
    orderTag: String,
    seconds: Long,
    nanos: Int,
): String {
    val seed = "$strategyExecutionId:$orderTag:$seconds:$nanos"
    return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
}

package com.example.stockpurchaseservice.adapter.out.kafka

import Event
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.OrderCancelledMessage
import com.example.stockpurchaseservice.application.port.out.OrderFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderPartiallyFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import common.proto.ProtoUtils.toProtobufTimestamp
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
internal class OrderExecutionKafkaSerializer {
    fun serialize(event: OrderSubmittedMessage): ByteArray = event.toProto().toByteArray()

    fun serialize(event: OrderRejectedMessage): ByteArray = event.toProto().toByteArray()

    fun serialize(event: OrderCancelledMessage): ByteArray = event.toProto().toByteArray()

    fun serialize(event: OrderFilledMessage): ByteArray = event.toProto().toByteArray()

    fun serialize(event: OrderPartiallyFilledMessage): ByteArray = event.toProto().toByteArray()

    private fun OrderSubmittedMessage.toProto(): Event.OrderSubmitted {
        return Event.OrderSubmitted.newBuilder()
            .setEventId(eventId.toString())
            .setStrategyExecutionId(strategyExecutionId)
            .setOrderIntentId(orderIntentId)
            .setBrokerOrderId(brokerOrderId)
            .setSide(side.toProto())
            .setOrderTag(orderTag)
            .setQuantity(quantity)
            .setSubmittedAt(submittedAt.toProtobufTimestamp())
            .setMeta(meta(submittedAt))
            .build()
    }

    private fun OrderRejectedMessage.toProto(): Event.OrderRejected {
        return Event.OrderRejected.newBuilder()
            .setEventId(eventId.toString())
            .setStrategyExecutionId(strategyExecutionId)
            .setOrderIntentId(orderIntentId)
            .setBrokerOrderId(brokerOrderId.orEmpty())
            .setReason(reason)
            .setRejectedAt(rejectedAt.toProtobufTimestamp())
            .setMeta(meta(rejectedAt))
            .build()
    }

    private fun OrderCancelledMessage.toProto(): Event.OrderCancelled {
        return Event.OrderCancelled.newBuilder()
            .setEventId(eventId.toString())
            .setStrategyExecutionId(strategyExecutionId)
            .setOrderIntentId(orderIntentId)
            .setBrokerOrderId(brokerOrderId)
            .setReason(reason)
            .setCancelledAt(cancelledAt.toProtobufTimestamp())
            .setMeta(meta(cancelledAt))
            .build()
    }

    private fun OrderFilledMessage.toProto(): Event.OrderFilled {
        return Event.OrderFilled.newBuilder()
            .setEventId(eventId.toString())
            .setStrategyExecutionId(strategyExecutionId)
            .setOrderIntentId(orderIntentId)
            .setBrokerOrderId(brokerOrderId)
            .setSide(side.toProto())
            .setFilledPrice(filledPrice)
            .setFilledQuantity(filledQuantity)
            .setOrderTag(orderTag)
            .setFilledAt(filledAt.toProtobufTimestamp())
            .setMeta(meta(filledAt))
            .build()
    }

    private fun OrderPartiallyFilledMessage.toProto(): Event.OrderPartiallyFilled {
        return Event.OrderPartiallyFilled.newBuilder()
            .setEventId(eventId.toString())
            .setStrategyExecutionId(strategyExecutionId)
            .setOrderIntentId(orderIntentId)
            .setBrokerOrderId(brokerOrderId)
            .setSide(side.toProto())
            .setFilledPrice(filledPrice)
            .setFilledQuantity(filledQuantity)
            .setOrderTag(orderTag)
            .setFilledAt(filledAt.toProtobufTimestamp())
            .setMeta(meta(filledAt))
            .build()
    }

    private fun meta(occurredAt: ZonedDateTime): Event.EventMeta.Builder {
        return Event.EventMeta.newBuilder()
            .setOccurredAt(occurredAt.toProtobufTimestamp())
            .setServiceName("stock-purchase-service")
    }

    private fun OrderIntentSide.toProto(): Event.OrderIntentSide {
        return when (this) {
            OrderIntentSide.BUY -> Event.OrderIntentSide.ORDER_INTENT_BUY
            OrderIntentSide.SELL -> Event.OrderIntentSide.ORDER_INTENT_SELL
        }
    }
}

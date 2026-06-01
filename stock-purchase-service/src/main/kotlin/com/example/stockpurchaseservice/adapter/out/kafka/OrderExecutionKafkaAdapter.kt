package com.example.stockpurchaseservice.adapter.out.kafka

import Event
import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.OrderExecutionEventPort
import com.example.stockpurchaseservice.application.port.out.OrderFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import common.Topic.ORDER_FILLED
import common.Topic.ORDER_REJECTED
import common.Topic.ORDER_SUBMITTED
import common.proto.ProtoUtils.toProtobufTimestamp
import kotlinx.coroutines.future.await
import org.springframework.kafka.core.KafkaTemplate

@ExternalApiAdapter
internal class OrderExecutionKafkaAdapter(
    private val kafkaProtoTypeTemplate: KafkaTemplate<String, ByteArray>,
) : OrderExecutionEventPort {

    override suspend fun publishSubmitted(event: OrderSubmittedMessage) {
        kafkaProtoTypeTemplate.send(
            ORDER_SUBMITTED,
            event.strategyExecutionId,
            event.toProto().toByteArray(),
        ).await()
    }

    override suspend fun publishRejected(event: OrderRejectedMessage) {
        kafkaProtoTypeTemplate.send(
            ORDER_REJECTED,
            event.strategyExecutionId,
            event.toProto().toByteArray(),
        ).await()
    }

    override suspend fun publishFilled(event: OrderFilledMessage) {
        kafkaProtoTypeTemplate.send(
            ORDER_FILLED,
            event.strategyExecutionId,
            event.toProto().toByteArray(),
        ).await()
    }

    private fun OrderSubmittedMessage.toProto(): Event.OrderSubmitted {
        return Event.OrderSubmitted.newBuilder()
            .setEventId(eventId.toString())
            .setStrategyExecutionId(strategyExecutionId)
            .setOrderIntentId(orderIntentId)
            .setBrokerOrderId(brokerOrderId)
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

    private fun meta(occurredAt: java.time.ZonedDateTime): Event.EventMeta.Builder {
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

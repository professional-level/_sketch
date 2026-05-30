package com.example.strategyexecutionservice.adapter.out.kafka

import Event
import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.out.OrderIntentMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentPort
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.execution.OrderType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType
import common.Topic.ORDER_INTENT_CREATED
import common.proto.ProtoUtils.toProtobufTimestamp
import kotlinx.coroutines.future.await
import org.springframework.kafka.core.KafkaTemplate

@ExternalApiAdapter
internal class OrderIntentKafkaAdapter(
    private val kafkaProtoTypeTemplate: KafkaTemplate<String, ByteArray>,
) : OrderIntentPort {

    override suspend fun publishAll(orderIntents: List<OrderIntentMessage>) {
        orderIntents.forEach { orderIntent ->
            kafkaProtoTypeTemplate.send(
                ORDER_INTENT_CREATED,
                orderIntent.strategyExecutionId,
                orderIntent.toProto().toByteArray(),
            ).await()
        }
    }

    private fun OrderIntentMessage.toProto(): Event.OrderIntentCreatedEvent {
        return Event.OrderIntentCreatedEvent.newBuilder()
            .setEventId(eventId.toString())
            .setStrategyExecutionId(strategyExecutionId)
            .setStrategyType(strategyType.toProto())
            .setSymbol(symbol)
            .setSide(side.toProto())
            .setOrderType(orderType.toProto())
            .setPrice(price ?: 0.0)
            .setQuantity(quantity)
            .setOrderTag(orderTag)
            .setIdempotencyKey(idempotencyKey)
            .setCreatedAt(createdAt.toProtobufTimestamp())
            .setMeta(
                Event.EventMeta.newBuilder()
                    .setOccurredAt(createdAt.toProtobufTimestamp())
                    .setServiceName("strategy-execution-service"),
            )
            .build()
    }

    private fun StrategyExecutionType.toProto(): Event.StrategyExecutionType {
        return when (this) {
            StrategyExecutionType.LAOR_V4_STRATEGY -> Event.StrategyExecutionType.LAOR_V4_STRATEGY
            StrategyExecutionType.FINAL_PRICE_BATING_V1_STRATEGY ->
                Event.StrategyExecutionType.FINAL_PRICE_BATING_V1_STRATEGY
        }
    }

    private fun OrderSide.toProto(): Event.OrderIntentSide {
        return when (this) {
            OrderSide.BUY -> Event.OrderIntentSide.ORDER_INTENT_BUY
            OrderSide.SELL -> Event.OrderIntentSide.ORDER_INTENT_SELL
        }
    }

    private fun OrderType.toProto(): Event.OrderIntentOrderType {
        return when (this) {
            OrderType.LOC -> Event.OrderIntentOrderType.ORDER_INTENT_LOC
            OrderType.MOC -> Event.OrderIntentOrderType.ORDER_INTENT_MOC
            OrderType.LIMIT -> Event.OrderIntentOrderType.ORDER_INTENT_LIMIT
        }
    }
}

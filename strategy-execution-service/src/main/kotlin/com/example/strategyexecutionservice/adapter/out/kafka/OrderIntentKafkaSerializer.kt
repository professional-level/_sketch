package com.example.strategyexecutionservice.adapter.out.kafka

import Event
import com.example.strategyexecutionservice.application.port.out.OrderIntentMessage
import com.example.strategyexecutionservice.application.port.out.OrderTradingEnvironment
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.execution.OrderType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType
import common.proto.ProtoUtils.toProtobufTimestamp
import org.springframework.stereotype.Component

@Component
internal class OrderIntentKafkaSerializer {
    fun serialize(orderIntent: OrderIntentMessage): ByteArray {
        return orderIntent.toProto().toByteArray()
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
            .setTradingEnvironment(tradingEnvironment.toProto())
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

    private fun OrderTradingEnvironment.toProto(): Event.OrderTradingEnvironment {
        return when (this) {
            OrderTradingEnvironment.MOCK -> Event.OrderTradingEnvironment.ORDER_TRADING_ENVIRONMENT_MOCK
            OrderTradingEnvironment.LIVE -> Event.OrderTradingEnvironment.ORDER_TRADING_ENVIRONMENT_LIVE
        }
    }
}

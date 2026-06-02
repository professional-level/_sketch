package com.example.strategyexecutionservice.adapter.out.kafka

import Event
import com.example.strategyexecutionservice.application.port.out.OrderIntentMessage
import com.example.strategyexecutionservice.application.port.out.OrderTradingEnvironment
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.execution.OrderType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderIntentKafkaSerializerTest {

    @Test
    fun `serializes trading environment into order intent event`() {
        val serializer = OrderIntentKafkaSerializer()

        val event = Event.OrderIntentCreatedEvent.parseFrom(serializer.serialize(message()))

        assertEquals(Event.OrderTradingEnvironment.ORDER_TRADING_ENVIRONMENT_LIVE, event.tradingEnvironment)
        assertEquals("NYSE", event.exchange)
    }

    private fun message(): OrderIntentMessage {
        return OrderIntentMessage(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000101"),
            strategyExecutionId = "laor-v4-live:TQQQ",
            strategyType = StrategyExecutionType.LAOR_V4_STRATEGY,
            symbol = "TQQQ",
            side = OrderSide.BUY,
            orderType = OrderType.LOC,
            price = 100.0,
            quantity = 1,
            orderTag = "FIRST_BUY",
            idempotencyKey = "laor-v4-live:TQQQ:2026-06-02:FIRST_BUY:0",
            createdAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            tradingEnvironment = OrderTradingEnvironment.LIVE,
            exchange = "NYSE",
        )
    }
}

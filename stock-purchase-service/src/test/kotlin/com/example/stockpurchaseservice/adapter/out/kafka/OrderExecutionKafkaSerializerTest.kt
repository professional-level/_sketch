package com.example.stockpurchaseservice.adapter.out.kafka

import Event
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.OrderCancelledMessage
import com.example.stockpurchaseservice.application.port.out.OrderFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderPartiallyFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderExecutionKafkaSerializerTest {

    @Test
    fun `serializes order execution idempotency keys`() {
        val serializer = OrderExecutionKafkaSerializer()

        assertEquals(
            "intent-1:SUBMITTED",
            Event.OrderSubmitted.parseFrom(serializer.serialize(submitted())).idempotencyKey,
        )
        assertEquals(
            "intent-2:REJECTED",
            Event.OrderRejected.parseFrom(serializer.serialize(rejected())).idempotencyKey,
        )
        assertEquals(
            "intent-3:CANCELLED",
            Event.OrderCancelled.parseFrom(serializer.serialize(cancelled())).idempotencyKey,
        )
        assertEquals(
            "intent-4:FILLED:exec-1",
            Event.OrderFilled.parseFrom(serializer.serialize(filled())).idempotencyKey,
        )
        assertEquals(
            "intent-5:PARTIALLY_FILLED:exec-2",
            Event.OrderPartiallyFilled.parseFrom(serializer.serialize(partiallyFilled())).idempotencyKey,
        )
    }

    private fun submitted(): OrderSubmittedMessage {
        return OrderSubmittedMessage(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000301"),
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-1",
            brokerOrderId = "broker-1",
            side = OrderIntentSide.BUY,
            orderTag = "FIRST_BUY",
            quantity = 1,
            submittedAt = OCCURRED_AT,
            idempotencyKey = "intent-1:SUBMITTED",
        )
    }

    private fun rejected(): OrderRejectedMessage {
        return OrderRejectedMessage(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000302"),
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-2",
            brokerOrderId = null,
            reason = "broker rejected",
            rejectedAt = OCCURRED_AT,
            idempotencyKey = "intent-2:REJECTED",
        )
    }

    private fun cancelled(): OrderCancelledMessage {
        return OrderCancelledMessage(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000303"),
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-3",
            brokerOrderId = "broker-3",
            reason = "cancelled",
            cancelledAt = OCCURRED_AT,
            idempotencyKey = "intent-3:CANCELLED",
        )
    }

    private fun filled(): OrderFilledMessage {
        return OrderFilledMessage(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000304"),
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-4",
            brokerOrderId = "broker-4",
            side = OrderIntentSide.BUY,
            filledPrice = 100.5,
            filledQuantity = 1,
            orderTag = "FIRST_BUY",
            filledAt = OCCURRED_AT,
            idempotencyKey = "intent-4:FILLED:exec-1",
        )
    }

    private fun partiallyFilled(): OrderPartiallyFilledMessage {
        return OrderPartiallyFilledMessage(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000305"),
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-5",
            brokerOrderId = "broker-5",
            side = OrderIntentSide.SELL,
            filledPrice = 101.0,
            filledQuantity = 1,
            orderTag = "FIRST_SELL",
            filledAt = OCCURRED_AT,
            idempotencyKey = "intent-5:PARTIALLY_FILLED:exec-2",
        )
    }

    private companion object {
        val OCCURRED_AT: ZonedDateTime = ZonedDateTime.parse("2026-06-02T09:00:00+09:00")
    }
}

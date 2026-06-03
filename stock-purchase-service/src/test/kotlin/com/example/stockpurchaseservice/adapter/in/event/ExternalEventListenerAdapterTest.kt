package com.example.stockpurchaseservice.adapter.`in`.event

import Event
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentResult
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentUseCase
import com.example.stockpurchaseservice.application.port.out.OrderTradingEnvironment
import com.google.protobuf.Timestamp
import common.Topic.ORDER_INTENT_CREATED
import common.observability.TraceContext
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ExternalEventListenerAdapterTest {

    @Test
    fun `restores trace context from Kafka headers while handling order intent`() = runBlocking {
        val useCase = CapturingSubmitOrderIntentUseCase()
        val adapter = ExternalEventListenerAdapter(useCase)
        val record = orderIntentRecord().withHeader(TraceContext.TRACEPARENT_KEY, TRACE_PARENT)
            .withHeader(TraceContext.TRACE_ID_KEY, TRACE_ID)
            .withHeader(TraceContext.SPAN_ID_KEY, SPAN_ID)

        MDC.put(TraceContext.TRACE_ID_KEY, "previous-trace")
        try {
            adapter.orderIntents(record)
        } finally {
            assertEquals("previous-trace", MDC.get(TraceContext.TRACE_ID_KEY))
            assertNull(MDC.get(TraceContext.SPAN_ID_KEY))
            assertNull(MDC.get(TraceContext.TRACEPARENT_KEY))
            MDC.clear()
        }

        assertEquals(Triple(TRACE_ID, SPAN_ID, TRACE_PARENT), useCase.observedTrace)
        assertEquals(1, useCase.commands.size)
        assertEquals(UUID.fromString(EVENT_ID), useCase.commands.single().eventId)
        assertEquals("intent-1", useCase.commands.single().idempotencyKey)
        assertEquals("laor-v4:TQQQ", useCase.commands.single().strategyExecutionId)
        assertEquals("TQQQ", useCase.commands.single().symbol)
        assertEquals("NYSE", useCase.commands.single().exchange)
        assertEquals(OrderIntentSide.BUY, useCase.commands.single().side)
        assertEquals(OrderIntentType.LOC, useCase.commands.single().orderType)
        assertEquals(100.0, useCase.commands.single().price)
        assertEquals(1L, useCase.commands.single().quantity)
        assertEquals("FIRST_BUY", useCase.commands.single().orderTag)
        assertEquals(Instant.ofEpochSecond(CREATED_AT_EPOCH_SECONDS), useCase.commands.single().createdAt.toInstant())
        assertEquals(OrderTradingEnvironment.MOCK, useCase.commands.single().tradingEnvironment)
    }

    @Test
    fun `maps sell moc order intent with zero price to no-price command`() = runBlocking {
        val useCase = CapturingSubmitOrderIntentUseCase()
        val adapter = ExternalEventListenerAdapter(useCase)
        val event = Event.OrderIntentCreatedEvent.newBuilder()
            .setEventId("00000000-0000-0000-0000-000000000102")
            .setIdempotencyKey("intent-sell-moc-1")
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setStrategyType(Event.StrategyExecutionType.LAOR_V4_STRATEGY)
            .setSymbol("TQQQ")
            .setExchange("NASD")
            .setSide(Event.OrderIntentSide.ORDER_INTENT_SELL)
            .setOrderType(Event.OrderIntentOrderType.ORDER_INTENT_MOC)
            .setPrice(0.0)
            .setQuantity(3L)
            .setOrderTag("MOC_SELL")
            .setCreatedAt(Timestamp.newBuilder().setSeconds(CREATED_AT_EPOCH_SECONDS).build())
            .setTradingEnvironment(Event.OrderTradingEnvironment.ORDER_TRADING_ENVIRONMENT_LIVE)
            .build()

        adapter.orderIntents(ConsumerRecord(ORDER_INTENT_CREATED, 0, 0L, "laor-v4:TQQQ", event.toByteArray()))

        val command = useCase.commands.single()
        assertEquals(OrderIntentSide.SELL, command.side)
        assertEquals(OrderIntentType.MOC, command.orderType)
        assertNull(command.price)
        assertEquals(3L, command.quantity)
        assertEquals("MOC_SELL", command.orderTag)
        assertEquals(OrderTradingEnvironment.LIVE, command.tradingEnvironment)
    }

    @Test
    fun `preserves negative moc price so command validation rejects the event`() = runBlocking {
        val useCase = CapturingSubmitOrderIntentUseCase()
        val adapter = ExternalEventListenerAdapter(useCase)
        val event = Event.OrderIntentCreatedEvent.newBuilder()
            .setEventId("00000000-0000-0000-0000-000000000103")
            .setIdempotencyKey("intent-sell-moc-negative")
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setStrategyType(Event.StrategyExecutionType.LAOR_V4_STRATEGY)
            .setSymbol("TQQQ")
            .setExchange("NASD")
            .setSide(Event.OrderIntentSide.ORDER_INTENT_SELL)
            .setOrderType(Event.OrderIntentOrderType.ORDER_INTENT_MOC)
            .setPrice(-1.0)
            .setQuantity(3L)
            .setOrderTag("MOC_SELL")
            .setCreatedAt(Timestamp.newBuilder().setSeconds(CREATED_AT_EPOCH_SECONDS).build())
            .setTradingEnvironment(Event.OrderTradingEnvironment.ORDER_TRADING_ENVIRONMENT_LIVE)
            .build()

        assertFailsWith<IllegalArgumentException> {
            adapter.orderIntents(ConsumerRecord(ORDER_INTENT_CREATED, 0, 0L, "laor-v4:TQQQ", event.toByteArray()))
        }
        assertEquals(emptyList(), useCase.commands)
    }

    private fun orderIntentRecord(): ConsumerRecord<String, ByteArray> {
        val event = Event.OrderIntentCreatedEvent.newBuilder()
            .setEventId(EVENT_ID)
            .setIdempotencyKey("intent-1")
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setStrategyType(Event.StrategyExecutionType.LAOR_V4_STRATEGY)
            .setSymbol("TQQQ")
            .setExchange("NYSE")
            .setSide(Event.OrderIntentSide.ORDER_INTENT_BUY)
            .setOrderType(Event.OrderIntentOrderType.ORDER_INTENT_LOC)
            .setPrice(100.0)
            .setQuantity(1L)
            .setOrderTag("FIRST_BUY")
            .setCreatedAt(Timestamp.newBuilder().setSeconds(CREATED_AT_EPOCH_SECONDS).build())
            .setTradingEnvironment(Event.OrderTradingEnvironment.ORDER_TRADING_ENVIRONMENT_MOCK)
            .build()
        return ConsumerRecord(ORDER_INTENT_CREATED, 0, 0L, "laor-v4:TQQQ", event.toByteArray())
    }

    private fun ConsumerRecord<String, ByteArray>.withHeader(
        key: String,
        value: String,
    ): ConsumerRecord<String, ByteArray> {
        headers().add(key, value.toByteArray(StandardCharsets.UTF_8))
        return this
    }

    private class CapturingSubmitOrderIntentUseCase : SubmitOrderIntentUseCase {
        val commands: MutableList<SubmitOrderIntentCommand> = mutableListOf()
        var observedTrace: Triple<String?, String?, String?>? = null

        override suspend fun execute(command: SubmitOrderIntentCommand): SubmitOrderIntentResult {
            commands += command
            observedTrace = Triple(
                MDC.get(TraceContext.TRACE_ID_KEY),
                MDC.get(TraceContext.SPAN_ID_KEY),
                MDC.get(TraceContext.TRACEPARENT_KEY),
            )
            return SubmitOrderIntentResult(OrderIntentSubmissionStatus.SUBMITTED)
        }
    }

    private companion object {
        const val EVENT_ID = "00000000-0000-0000-0000-000000000101"
        const val CREATED_AT_EPOCH_SECONDS = 1_754_092_800L
        const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        const val SPAN_ID = "00f067aa0ba902b7"
        const val TRACE_PARENT = "00-$TRACE_ID-$SPAN_ID-01"
    }
}

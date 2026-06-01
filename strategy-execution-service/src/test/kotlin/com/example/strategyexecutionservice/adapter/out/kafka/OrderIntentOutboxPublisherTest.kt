package com.example.strategyexecutionservice.adapter.out.kafka

import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxPort
import common.MessageTopic
import common.observability.TraceContext
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class OrderIntentOutboxPublisherTest {

    @Test
    fun `marks outbox event published after successful Kafka publish`() = runBlocking {
        val event = outboxMessage()
        val outboxPort = FakeOrderIntentOutboxPort(events = listOf(event))
        val sender = FakeOrderIntentOutboxMessageSender()
        val publisher = OrderIntentOutboxPublisher(outboxPort, sender)

        publisher.publishPendingEvents()

        assertEquals(listOf(event), sender.published)
        assertEquals(listOf(event.id), outboxPort.publishedIds)
        assertEquals(emptyList(), outboxPort.failed)
    }

    @Test
    fun `marks outbox event failed when Kafka publish fails`() = runBlocking {
        val event = outboxMessage()
        val outboxPort = FakeOrderIntentOutboxPort(events = listOf(event))
        val sender = FakeOrderIntentOutboxMessageSender(failure = IllegalStateException("kafka down"))
        val publisher = OrderIntentOutboxPublisher(outboxPort, sender)

        publisher.publishPendingEvents()

        assertEquals(emptyList(), outboxPort.publishedIds)
        assertEquals(listOf<Pair<UUID, String?>>(event.id to "kafka down"), outboxPort.failed)
    }

    @Test
    fun `producer record includes trace headers`() {
        val record = outboxMessage(
            traceId = TRACE_ID,
            spanId = SPAN_ID,
            traceParent = TRACE_PARENT,
        ).toProducerRecord()

        assertEquals(MessageTopic.ORDER_INTENT_CREATED.topicName, record.topic())
        assertEquals("laor-v4:TQQQ", record.key())
        assertContentEquals(byteArrayOf(1, 2, 3), record.value())
        assertEquals(TRACE_PARENT, record.headerValue(TraceContext.TRACEPARENT_KEY))
        assertEquals(TRACE_ID, record.headerValue(TraceContext.TRACE_ID_KEY))
        assertEquals(SPAN_ID, record.headerValue(TraceContext.SPAN_ID_KEY))
    }

    private fun outboxMessage(
        traceId: String? = null,
        spanId: String? = null,
        traceParent: String? = null,
    ): OrderIntentOutboxMessage {
        return OrderIntentOutboxMessage(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            topic = MessageTopic.ORDER_INTENT_CREATED,
            messageKey = "laor-v4:TQQQ",
            payload = byteArrayOf(1, 2, 3),
            retryCount = 0,
            traceId = traceId,
            spanId = spanId,
            traceParent = traceParent,
        )
    }

    private fun org.apache.kafka.clients.producer.ProducerRecord<String, ByteArray>.headerValue(key: String): String? {
        return headers().lastHeader(key)?.value()?.toString(StandardCharsets.UTF_8)
    }

    private class FakeOrderIntentOutboxPort(
        private val events: List<OrderIntentOutboxMessage>,
    ) : OrderIntentOutboxPort {
        val publishedIds: MutableList<UUID> = mutableListOf()
        val failed: MutableList<Pair<UUID, String?>> = mutableListOf()

        override suspend fun findUnpublished(limit: Int): List<OrderIntentOutboxMessage> {
            return events.take(limit)
        }

        override suspend fun markPublished(id: UUID) {
            publishedIds += id
        }

        override suspend fun markFailed(id: UUID, reason: String?) {
            failed += id to reason
        }
    }

    private class FakeOrderIntentOutboxMessageSender(
        private val failure: RuntimeException? = null,
    ) : OrderIntentOutboxMessageSender {
        val published: MutableList<OrderIntentOutboxMessage> = mutableListOf()

        override suspend fun publish(event: OrderIntentOutboxMessage) {
            failure?.let { throw it }
            published += event
        }
    }

    private companion object {
        const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        const val SPAN_ID = "00f067aa0ba902b7"
        const val TRACE_PARENT = "00-$TRACE_ID-$SPAN_ID-01"
    }
}

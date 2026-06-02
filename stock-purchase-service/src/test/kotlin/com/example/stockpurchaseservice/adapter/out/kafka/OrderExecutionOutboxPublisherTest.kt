package com.example.stockpurchaseservice.adapter.out.kafka

import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxPort
import common.MessageTopic
import common.observability.TraceContext
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class OrderExecutionOutboxPublisherTest {

    @Test
    fun `marks outbox event published after successful Kafka publish`() = runBlocking {
        val event = outboxMessage()
        val outboxPort = FakeOrderExecutionOutboxPort(events = listOf(event))
        val sender = FakeOrderExecutionOutboxMessageSender()
        val meterRegistry = SimpleMeterRegistry()
        val publisher = OrderExecutionOutboxPublisher(outboxPort, sender, meterRegistry)

        publisher.publishPendingEvents()

        val claimRequest = outboxPort.claimRequests.single()
        assertEquals(listOf(event), sender.published)
        assertEquals(listOf(PublishedOutboxEvent(event.id, claimRequest.claimOwner)), outboxPort.published)
        assertEquals(emptyList(), outboxPort.failed)
        assertEquals(ClaimRequest(limit = 50, claimExpiresAt = null), claimRequest.withoutOwner())
        assertEquals(1.0, meterRegistry.counter(OUTBOX_PUBLISH_METRIC, "result", "published").count())
        assertEquals(0.0, meterRegistry.counter(OUTBOX_PUBLISH_METRIC, "result", "failed").count())
    }

    @Test
    fun `marks outbox event failed when Kafka publish fails`() = runBlocking {
        val event = outboxMessage()
        val outboxPort = FakeOrderExecutionOutboxPort(events = listOf(event))
        val sender = FakeOrderExecutionOutboxMessageSender(failure = IllegalStateException("kafka down"))
        val meterRegistry = SimpleMeterRegistry()
        val publisher = OrderExecutionOutboxPublisher(outboxPort, sender, meterRegistry)
        publisher.clock = FIXED_CLOCK

        publisher.publishPendingEvents()

        val claimRequest = outboxPort.claimRequests.single()
        assertEquals(emptyList(), outboxPort.published)
        assertEquals(
            listOf(FailedOutboxEvent(event.id, claimRequest.claimOwner, "kafka down", FIXED_NOW.plusSeconds(5))),
            outboxPort.failed,
        )
        assertEquals(FIXED_NOW.plusSeconds(60), claimRequest.claimExpiresAt)
        assertEquals(0.0, meterRegistry.counter(OUTBOX_PUBLISH_METRIC, "result", "published").count())
        assertEquals(1.0, meterRegistry.counter(OUTBOX_PUBLISH_METRIC, "result", "failed").count())
    }

    @Test
    fun `caps outbox publish retry delay`() = runBlocking {
        val event = outboxMessage(retryCount = 10)
        val outboxPort = FakeOrderExecutionOutboxPort(events = listOf(event))
        val sender = FakeOrderExecutionOutboxMessageSender(failure = IllegalStateException("kafka down"))
        val publisher = OrderExecutionOutboxPublisher(
            outboxPort,
            sender,
            SimpleMeterRegistry(),
            retryInitialDelayMs = 1_000,
            retryMaxDelayMs = 60_000,
        )
        publisher.clock = FIXED_CLOCK

        publisher.publishPendingEvents()

        val claimRequest = outboxPort.claimRequests.single()
        assertEquals(
            listOf(FailedOutboxEvent(event.id, claimRequest.claimOwner, "kafka down", FIXED_NOW.plusSeconds(60))),
            outboxPort.failed,
        )
    }

    @Test
    fun `producer record includes trace headers`() {
        val record = outboxMessage(
            traceId = TRACE_ID,
            spanId = SPAN_ID,
            traceParent = TRACE_PARENT,
        ).toProducerRecord()

        assertEquals(MessageTopic.ORDER_FILLED.topicName, record.topic())
        assertEquals("laor-v4:TQQQ", record.key())
        assertContentEquals(byteArrayOf(1, 2, 3), record.value())
        assertEquals(TRACE_PARENT, record.headerValue(TraceContext.TRACEPARENT_KEY))
        assertEquals(TRACE_ID, record.headerValue(TraceContext.TRACE_ID_KEY))
        assertEquals(SPAN_ID, record.headerValue(TraceContext.SPAN_ID_KEY))
    }

    private fun outboxMessage(
        retryCount: Int = 0,
        traceId: String? = null,
        spanId: String? = null,
        traceParent: String? = null,
    ): OrderExecutionOutboxMessage {
        return OrderExecutionOutboxMessage(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            topic = MessageTopic.ORDER_FILLED,
            messageKey = "laor-v4:TQQQ",
            payload = byteArrayOf(1, 2, 3),
            retryCount = retryCount,
            traceId = traceId,
            spanId = spanId,
            traceParent = traceParent,
        )
    }

    private fun org.apache.kafka.clients.producer.ProducerRecord<String, ByteArray>.headerValue(key: String): String? {
        return headers().lastHeader(key)?.value()?.toString(StandardCharsets.UTF_8)
    }

    private class FakeOrderExecutionOutboxPort(
        private val events: List<OrderExecutionOutboxMessage>,
    ) : OrderExecutionOutboxPort {
        val published: MutableList<PublishedOutboxEvent> = mutableListOf()
        val failed: MutableList<FailedOutboxEvent> = mutableListOf()
        val claimRequests: MutableList<ClaimRequest> = mutableListOf()

        override suspend fun claimPublishable(
            limit: Int,
            claimOwner: String,
            claimExpiresAt: ZonedDateTime,
        ): List<OrderExecutionOutboxMessage> {
            claimRequests += ClaimRequest(limit, claimOwner, claimExpiresAt)
            return events.take(limit)
        }

        override suspend fun markPublished(id: UUID, claimOwner: String) {
            published += PublishedOutboxEvent(id, claimOwner)
        }

        override suspend fun markFailed(id: UUID, claimOwner: String, reason: String?, nextAttemptAt: ZonedDateTime) {
            failed += FailedOutboxEvent(id, claimOwner, reason, nextAttemptAt)
        }
    }

    private data class PublishedOutboxEvent(
        val id: UUID,
        val claimOwner: String?,
    )

    private data class FailedOutboxEvent(
        val id: UUID,
        val claimOwner: String?,
        val reason: String?,
        val nextAttemptAt: ZonedDateTime,
    )

    private data class ClaimRequest(
        val limit: Int,
        val claimOwner: String? = null,
        val claimExpiresAt: ZonedDateTime? = null,
    ) {
        fun withoutOwner(): ClaimRequest {
            return copy(claimOwner = null, claimExpiresAt = null)
        }
    }

    private class FakeOrderExecutionOutboxMessageSender(
        private val failure: RuntimeException? = null,
    ) : OrderExecutionOutboxMessageSender {
        val published: MutableList<OrderExecutionOutboxMessage> = mutableListOf()

        override suspend fun publish(event: OrderExecutionOutboxMessage) {
            failure?.let { throw it }
            published += event
        }
    }

    private companion object {
        const val OUTBOX_PUBLISH_METRIC = "stock.purchase.outbox.publish"
        const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        const val SPAN_ID = "00f067aa0ba902b7"
        const val TRACE_PARENT = "00-$TRACE_ID-$SPAN_ID-01"
        val FIXED_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-06-02T01:00:00Z"), FIXED_ZONE)
        val FIXED_NOW: ZonedDateTime = ZonedDateTime.now(FIXED_CLOCK)
    }
}

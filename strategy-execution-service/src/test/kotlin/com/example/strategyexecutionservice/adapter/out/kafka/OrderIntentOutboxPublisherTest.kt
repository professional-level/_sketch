package com.example.strategyexecutionservice.adapter.out.kafka

import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxPort
import common.MessageTopic
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

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

    private fun outboxMessage(): OrderIntentOutboxMessage {
        return OrderIntentOutboxMessage(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            topic = MessageTopic.ORDER_INTENT_CREATED,
            messageKey = "laor-v4:TQQQ",
            payload = byteArrayOf(1, 2, 3),
            retryCount = 0,
        )
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
}

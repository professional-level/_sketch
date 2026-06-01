package com.example.stockpurchaseservice.adapter.out.kafka

import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxPort
import common.MessageTopic
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class OrderExecutionOutboxPublisherTest {

    @Test
    fun `marks outbox event published after successful Kafka publish`() = runBlocking {
        val event = outboxMessage()
        val outboxPort = FakeOrderExecutionOutboxPort(events = listOf(event))
        val sender = FakeOrderExecutionOutboxMessageSender()
        val publisher = OrderExecutionOutboxPublisher(outboxPort, sender)

        publisher.publishPendingEvents()

        assertEquals(listOf(event), sender.published)
        assertEquals(listOf(event.id), outboxPort.publishedIds)
        assertEquals(emptyList(), outboxPort.failed)
    }

    @Test
    fun `marks outbox event failed when Kafka publish fails`() = runBlocking {
        val event = outboxMessage()
        val outboxPort = FakeOrderExecutionOutboxPort(events = listOf(event))
        val sender = FakeOrderExecutionOutboxMessageSender(failure = IllegalStateException("kafka down"))
        val publisher = OrderExecutionOutboxPublisher(outboxPort, sender)

        publisher.publishPendingEvents()

        assertEquals(emptyList(), outboxPort.publishedIds)
        assertEquals(listOf<Pair<UUID, String?>>(event.id to "kafka down"), outboxPort.failed)
    }

    private fun outboxMessage(): OrderExecutionOutboxMessage {
        return OrderExecutionOutboxMessage(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            topic = MessageTopic.ORDER_FILLED,
            messageKey = "laor-v4:TQQQ",
            payload = byteArrayOf(1, 2, 3),
            retryCount = 0,
        )
    }

    private class FakeOrderExecutionOutboxPort(
        private val events: List<OrderExecutionOutboxMessage>,
    ) : OrderExecutionOutboxPort {
        val publishedIds: MutableList<UUID> = mutableListOf()
        val failed: MutableList<Pair<UUID, String?>> = mutableListOf()

        override suspend fun findUnpublished(limit: Int): List<OrderExecutionOutboxMessage> {
            return events.take(limit)
        }

        override suspend fun markPublished(id: UUID) {
            publishedIds += id
        }

        override suspend fun markFailed(id: UUID, reason: String?) {
            failed += id to reason
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
}

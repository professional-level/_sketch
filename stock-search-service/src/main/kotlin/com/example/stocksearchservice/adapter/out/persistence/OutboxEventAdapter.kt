package com.example.stocksearchservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.common.application.event.ApplicationEvent
import com.example.common.application.event.EventMessage
import com.example.stocksearchservice.adapter.out.kafka.StockSearchKafkaEventSerializer
import com.example.stocksearchservice.adapter.out.persistence.entity.OutboxEvent
import com.example.stocksearchservice.adapter.out.persistence.repository.OutboxEventRepository
import com.example.stocksearchservice.application.port.out.message.OutboxEventDto
import com.example.stocksearchservice.application.port.out.message.OutboxEventPort
import common.MessageTopic
import io.smallrye.mutiny.coroutines.awaitSuspending
import java.util.UUID

@PersistenceAdapter
internal class OutboxEventAdapter(
    private val outboxEventRepository: OutboxEventRepository,
    private val serializer: StockSearchKafkaEventSerializer,
) : OutboxEventPort {

    override suspend fun saveAll(messages: List<EventMessage<ApplicationEvent>>) {
        messages.forEach { message ->
            val event = message.convertedEvent
            outboxEventRepository.save(
                OutboxEvent.pending(
                    id = event.id,
                    topic = message.messageTopic,
                    eventType = event::class.java.simpleName,
                    payload = serializer.serialize(event),
                ),
            ).awaitSuspending()
        }
    }

    override suspend fun findUnpublished(limit: Int): List<OutboxEventDto> {
        return outboxEventRepository.findUnpublished(limit).map { event ->
            OutboxEventDto(
                id = event.id,
                topic = MessageTopic.fromOrThrow(event.topic.topicName),
                payload = event.payload,
                retryCount = event.retryCount,
            )
        }
    }

    override suspend fun markPublished(id: UUID) {
        val event = outboxEventRepository.findById(id).awaitSuspending() ?: return
        event.published()
        outboxEventRepository.update(event)
    }

    override suspend fun markFailed(id: UUID, reason: String?) {
        val event = outboxEventRepository.findById(id).awaitSuspending() ?: return
        event.failed(reason)
        outboxEventRepository.update(event)
    }
}

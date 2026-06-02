package com.example.strategyexecutionservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.strategyexecutionservice.adapter.out.kafka.OrderIntentKafkaSerializer
import com.example.strategyexecutionservice.adapter.out.persistence.entity.OrderIntentOutboxEventEntity
import com.example.strategyexecutionservice.adapter.out.persistence.repository.OrderIntentOutboxEventRepository
import com.example.strategyexecutionservice.application.port.out.OrderIntentMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxPort
import com.example.strategyexecutionservice.application.port.out.OrderIntentPort
import common.MessageTopic
import common.observability.TraceContext
import io.smallrye.mutiny.coroutines.awaitSuspending
import java.time.ZonedDateTime
import java.util.UUID

@PersistenceAdapter
internal class OrderIntentOutboxAdapter(
    private val outboxEventRepository: OrderIntentOutboxEventRepository,
    private val serializer: OrderIntentKafkaSerializer,
) : OrderIntentPort, OrderIntentOutboxPort {

    override suspend fun publishAll(orderIntents: List<OrderIntentMessage>) {
        orderIntents.forEach { orderIntent ->
            val traceContext = TraceContext.current()
            val event = OrderIntentOutboxEventEntity.pending(
                id = orderIntent.eventId,
                topic = MessageTopic.ORDER_INTENT_CREATED,
                messageKey = orderIntent.strategyExecutionId,
                eventType = "OrderIntentCreatedEvent",
                payload = serializer.serialize(orderIntent),
                traceId = traceContext.traceId,
                spanId = traceContext.spanId,
                traceParent = traceContext.traceParent,
            )
            saveIfNew(event)
        }
    }

    private suspend fun saveIfNew(event: OrderIntentOutboxEventEntity) {
        if (outboxEventRepository.exists(event.id)) return

        runCatching {
            outboxEventRepository.save(event).awaitSuspending()
        }.onFailure { exception ->
            if (!outboxEventRepository.exists(event.id)) throw exception
        }
    }

    override suspend fun claimPublishable(
        limit: Int,
        claimOwner: String,
        claimExpiresAt: ZonedDateTime,
    ): List<OrderIntentOutboxMessage> {
        return outboxEventRepository.claimPublishable(limit, claimOwner, claimExpiresAt).map { event ->
            OrderIntentOutboxMessage(
                id = event.id,
                topic = MessageTopic.fromOrThrow(event.topic.topicName),
                messageKey = event.messageKey,
                payload = event.payload,
                retryCount = event.retryCount,
                traceId = event.traceId,
                spanId = event.spanId,
                traceParent = event.traceParent,
            )
        }
    }

    override suspend fun markPublished(id: UUID, claimOwner: String) {
        outboxEventRepository.markPublishedIfClaimed(id, claimOwner)
    }

    override suspend fun markFailed(
        id: UUID,
        claimOwner: String,
        reason: String?,
        nextAttemptAt: ZonedDateTime,
    ) {
        outboxEventRepository.markFailedIfClaimed(id, claimOwner, reason, nextAttemptAt)
    }
}

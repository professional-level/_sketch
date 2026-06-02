package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.adapter.out.kafka.OrderExecutionKafkaSerializer
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderExecutionOutboxEventEntity
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderExecutionOutboxEventRepository
import com.example.stockpurchaseservice.application.port.out.OrderCancelledMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionEventPort
import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxPort
import com.example.stockpurchaseservice.application.port.out.OrderFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderPartiallyFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import common.MessageTopic
import common.observability.TraceContext
import io.smallrye.mutiny.coroutines.awaitSuspending
import java.time.ZonedDateTime
import java.util.UUID

@PersistenceAdapter
internal class OrderExecutionOutboxAdapter(
    private val outboxEventRepository: OrderExecutionOutboxEventRepository,
    private val serializer: OrderExecutionKafkaSerializer,
) : OrderExecutionEventPort, OrderExecutionOutboxPort {

    override suspend fun publishSubmitted(event: OrderSubmittedMessage) {
        save(
            id = event.eventId,
            topic = MessageTopic.ORDER_SUBMITTED,
            messageKey = event.strategyExecutionId,
            eventType = "OrderSubmitted",
            payload = serializer.serialize(event),
        )
    }

    override suspend fun publishRejected(event: OrderRejectedMessage) {
        save(
            id = event.eventId,
            topic = MessageTopic.ORDER_REJECTED,
            messageKey = event.strategyExecutionId,
            eventType = "OrderRejected",
            payload = serializer.serialize(event),
        )
    }

    override suspend fun publishCancelled(event: OrderCancelledMessage) {
        save(
            id = event.eventId,
            topic = MessageTopic.ORDER_CANCELLED,
            messageKey = event.strategyExecutionId,
            eventType = "OrderCancelled",
            payload = serializer.serialize(event),
        )
    }

    override suspend fun publishFilled(event: OrderFilledMessage) {
        save(
            id = event.eventId,
            topic = MessageTopic.ORDER_FILLED,
            messageKey = event.strategyExecutionId,
            eventType = "OrderFilled",
            payload = serializer.serialize(event),
        )
    }

    override suspend fun publishPartiallyFilled(event: OrderPartiallyFilledMessage) {
        save(
            id = event.eventId,
            topic = MessageTopic.ORDER_PARTIALLY_FILLED,
            messageKey = event.strategyExecutionId,
            eventType = "OrderPartiallyFilled",
            payload = serializer.serialize(event),
        )
    }

    override suspend fun claimPublishable(
        limit: Int,
        claimOwner: String,
        claimExpiresAt: ZonedDateTime,
    ): List<OrderExecutionOutboxMessage> {
        return outboxEventRepository.claimPublishable(limit, claimOwner, claimExpiresAt).map { event ->
            OrderExecutionOutboxMessage(
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

    private suspend fun save(
        id: UUID,
        topic: MessageTopic,
        messageKey: String,
        eventType: String,
        payload: ByteArray,
    ) {
        if (outboxEventRepository.exists(id)) return

        val traceContext = TraceContext.current()
        runCatching {
            outboxEventRepository.save(
                OrderExecutionOutboxEventEntity.pending(
                    id = id,
                    topic = topic,
                    messageKey = messageKey,
                    eventType = eventType,
                    payload = payload,
                    traceId = traceContext.traceId,
                    spanId = traceContext.spanId,
                    traceParent = traceContext.traceParent,
                ),
            ).awaitSuspending()
        }.onFailure { exception ->
            if (!outboxEventRepository.exists(id)) throw exception
        }
    }
}

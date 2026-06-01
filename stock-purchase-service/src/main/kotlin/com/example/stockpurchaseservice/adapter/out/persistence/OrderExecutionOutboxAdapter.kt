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
import io.smallrye.mutiny.coroutines.awaitSuspending
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

    override suspend fun findUnpublished(limit: Int): List<OrderExecutionOutboxMessage> {
        return outboxEventRepository.findUnpublished(limit).map { event ->
            OrderExecutionOutboxMessage(
                id = event.id,
                topic = MessageTopic.fromOrThrow(event.topic.topicName),
                messageKey = event.messageKey,
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

    private suspend fun save(
        id: UUID,
        topic: MessageTopic,
        messageKey: String,
        eventType: String,
        payload: ByteArray,
    ) {
        outboxEventRepository.save(
            OrderExecutionOutboxEventEntity.pending(
                id = id,
                topic = topic,
                messageKey = messageKey,
                eventType = eventType,
                payload = payload,
            ),
        ).awaitSuspending()
    }
}

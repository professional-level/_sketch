package com.example.stockpurchaseservice.adapter.out.kafka

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxPort
import kotlinx.coroutines.future.await
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.kafka.core.KafkaTemplate

@ExternalApiAdapter
internal class OrderExecutionKafkaAdapter(
    private val kafkaProtoTypeTemplate: KafkaTemplate<String, ByteArray>,
) : OrderExecutionOutboxMessageSender {

    override suspend fun publish(event: OrderExecutionOutboxMessage) {
        kafkaProtoTypeTemplate.send(
            event.topic.topicName,
            event.messageKey,
            event.payload,
        ).await()
    }
}

internal interface OrderExecutionOutboxMessageSender {
    suspend fun publish(event: OrderExecutionOutboxMessage)
}

@ExternalApiAdapter
internal class OrderExecutionOutboxPublisher(
    private val outboxEventPort: OrderExecutionOutboxPort,
    private val messageSender: OrderExecutionOutboxMessageSender,
) {
    @Scheduled(fixedDelayString = "\${akra.outbox.publish-fixed-delay-ms:5000}")
    suspend fun publishPendingEvents() {
        outboxEventPort.findUnpublished(limit = 50).forEach { event ->
            runCatching {
                messageSender.publish(event)
            }.onSuccess {
                outboxEventPort.markPublished(event.id)
            }.onFailure { exception ->
                outboxEventPort.markFailed(event.id, exception.message)
            }
        }
    }
}

package com.example.strategyexecutionservice.adapter.out.kafka

import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxPort
import kotlinx.coroutines.future.await
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.kafka.core.KafkaTemplate

@ExternalApiAdapter
internal class OrderIntentKafkaAdapter(
    private val kafkaProtoTypeTemplate: KafkaTemplate<String, ByteArray>,
) : OrderIntentOutboxMessageSender {

    override suspend fun publish(event: OrderIntentOutboxMessage) {
        kafkaProtoTypeTemplate.send(
            event.topic.topicName,
            event.messageKey,
            event.payload,
        ).await()
    }
}

internal interface OrderIntentOutboxMessageSender {
    suspend fun publish(event: OrderIntentOutboxMessage)
}

@ExternalApiAdapter
internal class OrderIntentOutboxPublisher(
    private val outboxEventPort: OrderIntentOutboxPort,
    private val messageSender: OrderIntentOutboxMessageSender,
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

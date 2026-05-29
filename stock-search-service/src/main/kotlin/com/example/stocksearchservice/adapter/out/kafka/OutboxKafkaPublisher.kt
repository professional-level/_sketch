package com.example.stocksearchservice.adapter.out.kafka

import com.example.common.ExternalApiAdapter
import com.example.stocksearchservice.application.port.out.message.MessageServicePort
import com.example.stocksearchservice.application.port.out.message.OutboxEventPort
import org.springframework.scheduling.annotation.Scheduled

@ExternalApiAdapter
internal class OutboxKafkaPublisher(
    private val outboxEventPort: OutboxEventPort,
    private val messageServicePort: MessageServicePort,
) {
    @Scheduled(fixedDelayString = "\${akra.outbox.publish-fixed-delay-ms:5000}")
    suspend fun publishPendingEvents() {
        outboxEventPort.findUnpublished(limit = 50).forEach { event ->
            runCatching {
                messageServicePort.publish(event.topic, event.payload)
            }.onSuccess {
                outboxEventPort.markPublished(event.id)
            }.onFailure { exception ->
                outboxEventPort.markFailed(event.id, exception.message)
            }
        }
    }
}

package com.example.strategyexecutionservice.adapter.out.kafka

import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxPort
import common.observability.TraceContext
import kotlinx.coroutines.future.await
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.kafka.core.KafkaTemplate
import java.nio.charset.StandardCharsets

@ExternalApiAdapter
internal class OrderIntentKafkaAdapter(
    private val kafkaProtoTypeTemplate: KafkaTemplate<String, ByteArray>,
) : OrderIntentOutboxMessageSender {

    override suspend fun publish(event: OrderIntentOutboxMessage) {
        kafkaProtoTypeTemplate.send(event.toProducerRecord()).await()
    }
}

internal fun OrderIntentOutboxMessage.toProducerRecord(): ProducerRecord<String, ByteArray> {
    return ProducerRecord(topic.topicName, messageKey, payload).also { record ->
        record.headers().addTraceContext(
            TraceContext(
                traceId = traceId,
                spanId = spanId,
                traceParent = traceParent,
            ),
        )
    }
}

private fun org.apache.kafka.common.header.Headers.addTraceContext(traceContext: TraceContext) {
    addHeaderIfPresent(TraceContext.TRACEPARENT_KEY, traceContext.traceParent)
    addHeaderIfPresent(TraceContext.TRACE_ID_KEY, traceContext.traceId)
    addHeaderIfPresent(TraceContext.SPAN_ID_KEY, traceContext.spanId)
}

private fun org.apache.kafka.common.header.Headers.addHeaderIfPresent(key: String, value: String?) {
    if (!value.isNullOrBlank()) {
        add(key, value.toByteArray(StandardCharsets.UTF_8))
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

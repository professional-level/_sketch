package com.example.stockpurchaseservice.adapter.out.kafka

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxPort
import common.observability.TraceContext
import kotlinx.coroutines.future.await
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.kafka.core.KafkaTemplate
import java.nio.charset.StandardCharsets

@ExternalApiAdapter
internal class OrderExecutionKafkaAdapter(
    private val kafkaProtoTypeTemplate: KafkaTemplate<String, ByteArray>,
) : OrderExecutionOutboxMessageSender {

    override suspend fun publish(event: OrderExecutionOutboxMessage) {
        kafkaProtoTypeTemplate.send(event.toProducerRecord()).await()
    }
}

internal fun OrderExecutionOutboxMessage.toProducerRecord(): ProducerRecord<String, ByteArray> {
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

package com.example.stockpurchaseservice.adapter.out.kafka

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionOutboxPort
import common.observability.TraceContext
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.future.await
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.beans.factory.annotation.Value
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID

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
    private val meterRegistry: MeterRegistry,
    @Value("\${akra.outbox.publish-retry-initial-delay-ms:5000}")
    private val retryInitialDelayMs: Long = DEFAULT_RETRY_INITIAL_DELAY_MS,
    @Value("\${akra.outbox.publish-retry-max-delay-ms:300000}")
    private val retryMaxDelayMs: Long = DEFAULT_RETRY_MAX_DELAY_MS,
    @Value("\${akra.outbox.publish-claim-lease-ms:60000}")
    private val claimLeaseMs: Long = DEFAULT_CLAIM_LEASE_MS,
) {
    internal var clock: Clock = Clock.systemDefaultZone()
    private val claimOwner: String = "stock-purchase-service-${UUID.randomUUID()}"

    @Scheduled(fixedDelayString = "\${akra.outbox.publish-fixed-delay-ms:5000}")
    suspend fun publishPendingEvents() {
        outboxEventPort.claimPublishable(
            limit = 50,
            claimOwner = claimOwner,
            claimExpiresAt = claimExpiresAt(),
        ).forEach { event ->
            runCatching {
                messageSender.publish(event)
            }.onSuccess {
                outboxEventPort.markPublished(event.id, claimOwner)
                recordPublishResult(PUBLISH_RESULT_PUBLISHED)
            }.onFailure { exception ->
                outboxEventPort.markFailed(event.id, claimOwner, exception.message, nextAttemptAt(event))
                recordPublishResult(PUBLISH_RESULT_FAILED)
            }
        }
    }

    private fun nextAttemptAt(event: OrderExecutionOutboxMessage): ZonedDateTime {
        return ZonedDateTime.now(clock).plus(retryDelay(event.retryCount))
    }

    private fun retryDelay(retryCount: Int): Duration {
        val initialDelay = retryInitialDelayMs.coerceAtLeast(0)
        val maxDelay = retryMaxDelayMs.coerceAtLeast(initialDelay)
        val multiplier = 1L shl retryCount.coerceIn(0, MAX_RETRY_SHIFT)
        return Duration.ofMillis((initialDelay * multiplier).coerceAtMost(maxDelay))
    }

    private fun claimExpiresAt(): ZonedDateTime {
        return ZonedDateTime.now(clock).plus(Duration.ofMillis(claimLeaseMs.coerceAtLeast(1)))
    }

    private fun recordPublishResult(result: String) {
        Counter.builder(OUTBOX_PUBLISH_METRIC)
            .tag("result", result)
            .register(meterRegistry)
            .increment()
    }

    companion object {
        private const val OUTBOX_PUBLISH_METRIC = "stock.purchase.outbox.publish"
        private const val PUBLISH_RESULT_PUBLISHED = "published"
        private const val PUBLISH_RESULT_FAILED = "failed"
        private const val DEFAULT_RETRY_INITIAL_DELAY_MS = 5_000L
        private const val DEFAULT_RETRY_MAX_DELAY_MS = 300_000L
        private const val DEFAULT_CLAIM_LEASE_MS = 60_000L
        private const val MAX_RETRY_SHIFT = 30
    }
}

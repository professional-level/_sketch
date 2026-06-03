package com.example.strategyexecutionservice.adapter.out.kafka

import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentOutboxPort
import common.observability.TraceContext
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.future.await
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.kafka.core.KafkaTemplate
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID

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
    private val meterRegistry: MeterRegistry,
    @Value("\${akra.outbox.publish-retry-initial-delay-ms:5000}")
    private val retryInitialDelayMs: Long = DEFAULT_RETRY_INITIAL_DELAY_MS,
    @Value("\${akra.outbox.publish-retry-max-delay-ms:300000}")
    private val retryMaxDelayMs: Long = DEFAULT_RETRY_MAX_DELAY_MS,
    @Value("\${akra.outbox.publish-claim-lease-ms:60000}")
    private val claimLeaseMs: Long = DEFAULT_CLAIM_LEASE_MS,
) {
    internal var clock: Clock = Clock.systemDefaultZone()
    private val claimOwner: String = "strategy-execution-service-${UUID.randomUUID()}"

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
                recordPublishResult(markPublishedResult(event))
            }.onFailure { exception ->
                recordPublishResult(markFailedResult(event, exception))
            }
        }
    }

    private suspend fun markPublishedResult(event: OrderIntentOutboxMessage): String {
        return runCatching {
            outboxEventPort.markPublished(event.id, claimOwner)
        }.fold(
            onSuccess = { markedPublished ->
                if (markedPublished) PUBLISH_RESULT_PUBLISHED else PUBLISH_RESULT_CLAIM_LOST
            },
            onFailure = { PUBLISH_RESULT_STATE_UPDATE_FAILED },
        )
    }

    private suspend fun markFailedResult(event: OrderIntentOutboxMessage, exception: Throwable): String {
        return runCatching {
            outboxEventPort.markFailed(event.id, claimOwner, exception.message, nextAttemptAt(event))
        }.fold(
            onSuccess = { markedFailed ->
                if (markedFailed) PUBLISH_RESULT_FAILED else PUBLISH_RESULT_CLAIM_LOST
            },
            onFailure = { PUBLISH_RESULT_STATE_UPDATE_FAILED },
        )
    }

    private fun nextAttemptAt(event: OrderIntentOutboxMessage): ZonedDateTime {
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
        private const val OUTBOX_PUBLISH_METRIC = "strategy.execution.outbox.publish"
        private const val PUBLISH_RESULT_PUBLISHED = "published"
        private const val PUBLISH_RESULT_FAILED = "failed"
        private const val PUBLISH_RESULT_CLAIM_LOST = "claim_lost"
        private const val PUBLISH_RESULT_STATE_UPDATE_FAILED = "state_update_failed"
        private const val DEFAULT_RETRY_INITIAL_DELAY_MS = 5_000L
        private const val DEFAULT_RETRY_MAX_DELAY_MS = 300_000L
        private const val DEFAULT_CLAIM_LEASE_MS = 60_000L
        private const val MAX_RETRY_SHIFT = 30
    }
}

package com.example.stockpurchaseservice.adapter.out.idempotency

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.application.port.out.ProcessedEventFastPathDecision
import com.example.stockpurchaseservice.application.port.out.ProcessedEventFastPathPort
import com.example.stockpurchaseservice.application.port.out.ProcessedEventPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import java.util.UUID

@Primary
@PersistenceAdapter
@ConditionalOnProperty(
    prefix = "akra.redis.processed-event",
    name = ["enabled"],
    havingValue = "true",
)
internal class RedisBackedProcessedEventAdapter(
    @Qualifier("processedEventAdapter") private val delegate: ProcessedEventPort,
    private val fastPath: ProcessedEventFastPathPort,
) : ProcessedEventPort {

    override suspend fun tryStart(eventId: UUID, idempotencyKey: String): Boolean {
        return when (fastPath.tryAcquire(eventId, idempotencyKey)) {
            ProcessedEventFastPathDecision.DUPLICATE -> false
            ProcessedEventFastPathDecision.UNAVAILABLE -> delegate.tryStart(eventId, idempotencyKey)
            ProcessedEventFastPathDecision.ACQUIRED -> tryStartAfterFastPathAcquire(eventId, idempotencyKey)
        }
    }

    private suspend fun tryStartAfterFastPathAcquire(eventId: UUID, idempotencyKey: String): Boolean {
        return try {
            val started = delegate.tryStart(eventId, idempotencyKey)
            if (!started) {
                fastPath.markCompleted(eventId)
            }
            started
        } catch (exception: Throwable) {
            fastPath.release(eventId)
            throw exception
        }
    }

    override suspend fun markSuccess(eventId: UUID) {
        delegate.markSuccess(eventId)
        fastPath.markCompleted(eventId)
    }

    override suspend fun markFailed(eventId: UUID, reason: String?) {
        delegate.markFailed(eventId, reason)
        fastPath.release(eventId)
    }
}

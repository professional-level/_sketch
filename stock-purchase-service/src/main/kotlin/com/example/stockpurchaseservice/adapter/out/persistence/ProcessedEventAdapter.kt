package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.adapter.out.persistence.entity.ProcessedEvent
import com.example.stockpurchaseservice.adapter.out.persistence.repository.ProcessedEventRepository
import com.example.stockpurchaseservice.application.port.out.ProcessedEventPort
import io.smallrye.mutiny.coroutines.awaitSuspending
import java.util.UUID

@PersistenceAdapter
internal class ProcessedEventAdapter(
    private val processedEventRepository: ProcessedEventRepository,
) : ProcessedEventPort {

    override suspend fun tryStart(eventId: UUID, idempotencyKey: String): Boolean {
        if (processedEventRepository.findById(eventId).awaitSuspending() != null) return false
        if (processedEventRepository.existsByIdempotencyKey(idempotencyKey)) return false
        processedEventRepository.save(ProcessedEvent.started(eventId, idempotencyKey)).awaitSuspending()
        return true
    }

    override suspend fun markSuccess(eventId: UUID) {
        val event = processedEventRepository.findById(eventId).awaitSuspending() ?: return
        event.success()
        processedEventRepository.update(event)
    }

    override suspend fun markFailed(eventId: UUID, reason: String?) {
        val event = processedEventRepository.findById(eventId).awaitSuspending() ?: return
        event.failed(reason)
        processedEventRepository.update(event)
    }
}

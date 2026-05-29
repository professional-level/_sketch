package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.ProcessedEvent
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository
import java.util.UUID

@ApplicationScoped
@Repository
internal class ProcessedEventRepository : AbstractReactiveRepository<ProcessedEvent, UUID>() {
    suspend fun existsByIdempotencyKey(idempotencyKey: String): Boolean {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "SELECT COUNT(e) FROM ProcessedEvent e WHERE e.idempotencyKey = :idempotencyKey",
                java.lang.Long::class.java,
            ).setParameter("idempotencyKey", idempotencyKey).singleResult
        }.awaitSuspending() > 0
    }

    suspend fun update(event: ProcessedEvent) {
        sessionFactory.withSession { session ->
            session.merge(event).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

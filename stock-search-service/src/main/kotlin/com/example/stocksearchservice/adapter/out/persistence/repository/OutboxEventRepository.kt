package com.example.stocksearchservice.adapter.out.persistence.repository

import com.example.stocksearchservice.adapter.out.persistence.entity.OutboxEvent
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository
import java.util.UUID

@ApplicationScoped
@Repository
internal class OutboxEventRepository : AbstractReactiveRepository<OutboxEvent, UUID>() {
    suspend fun findUnpublished(limit: Int): List<OutboxEvent> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OutboxEvent e WHERE e.status IN ('PENDING', 'FAILED') ORDER BY e.createdAt ASC",
                OutboxEvent::class.java,
            ).setMaxResults(limit).resultList
        }.awaitSuspending()
    }

    suspend fun update(event: OutboxEvent) {
        sessionFactory.withSession { session ->
            session.merge(event).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

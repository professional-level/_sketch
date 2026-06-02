package com.example.strategyexecutionservice.adapter.out.persistence.repository

import com.example.strategyexecutionservice.adapter.out.persistence.entity.OrderIntentOutboxEventEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime
import java.util.UUID

@ApplicationScoped
@Repository
internal class OrderIntentOutboxEventRepository : AbstractReactiveRepository<OrderIntentOutboxEventEntity, UUID>() {
    suspend fun findUnpublished(limit: Int): List<OrderIntentOutboxEventEntity> {
        val now = ZonedDateTime.now()
        return sessionFactory.withSession { session ->
            session.createQuery(
                """
                FROM OrderIntentOutboxEventEntity e
                WHERE e.status = 'PENDING'
                   OR (e.status = 'FAILED' AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now))
                ORDER BY e.createdAt ASC
                """.trimIndent(),
                OrderIntentOutboxEventEntity::class.java,
            ).setParameter("now", now)
                .setMaxResults(limit)
                .resultList
        }.awaitSuspending()
    }

    suspend fun update(event: OrderIntentOutboxEventEntity) {
        sessionFactory.withSession { session ->
            session.merge(event).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

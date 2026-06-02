package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderExecutionOutboxEventEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime
import java.util.UUID

@ApplicationScoped
@Repository
internal class OrderExecutionOutboxEventRepository : AbstractReactiveRepository<OrderExecutionOutboxEventEntity, UUID>() {
    suspend fun findUnpublished(limit: Int): List<OrderExecutionOutboxEventEntity> {
        val now = ZonedDateTime.now()
        return sessionFactory.withSession { session ->
            session.createQuery(
                """
                FROM OrderExecutionOutboxEventEntity e
                WHERE e.status = 'PENDING'
                   OR (e.status = 'FAILED' AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now))
                ORDER BY e.createdAt ASC
                """.trimIndent(),
                OrderExecutionOutboxEventEntity::class.java,
            ).setParameter("now", now)
                .setMaxResults(limit)
                .resultList
        }.awaitSuspending()
    }

    suspend fun update(event: OrderExecutionOutboxEventEntity) {
        sessionFactory.withSession { session ->
            session.merge(event).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

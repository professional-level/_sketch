package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderExecutionOutboxEventEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository
import java.util.UUID

@ApplicationScoped
@Repository
internal class OrderExecutionOutboxEventRepository : AbstractReactiveRepository<OrderExecutionOutboxEventEntity, UUID>() {
    suspend fun findUnpublished(limit: Int): List<OrderExecutionOutboxEventEntity> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OrderExecutionOutboxEventEntity e WHERE e.status IN ('PENDING', 'FAILED') ORDER BY e.createdAt ASC",
                OrderExecutionOutboxEventEntity::class.java,
            ).setMaxResults(limit).resultList
        }.awaitSuspending()
    }

    suspend fun update(event: OrderExecutionOutboxEventEntity) {
        sessionFactory.withSession { session ->
            session.merge(event).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

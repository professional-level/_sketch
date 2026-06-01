package com.example.strategyexecutionservice.adapter.out.persistence.repository

import com.example.strategyexecutionservice.adapter.out.persistence.entity.OrderIntentOutboxEventEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository
import java.util.UUID

@ApplicationScoped
@Repository
internal class OrderIntentOutboxEventRepository : AbstractReactiveRepository<OrderIntentOutboxEventEntity, UUID>() {
    suspend fun findUnpublished(limit: Int): List<OrderIntentOutboxEventEntity> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OrderIntentOutboxEventEntity e WHERE e.status IN ('PENDING', 'FAILED') ORDER BY e.createdAt ASC",
                OrderIntentOutboxEventEntity::class.java,
            ).setMaxResults(limit).resultList
        }.awaitSuspending()
    }

    suspend fun update(event: OrderIntentOutboxEventEntity) {
        sessionFactory.withSession { session ->
            session.merge(event).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

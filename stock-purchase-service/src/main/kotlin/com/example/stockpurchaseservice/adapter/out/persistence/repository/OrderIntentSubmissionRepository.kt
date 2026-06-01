package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionEntity
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionStatus
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository
import java.util.UUID

@ApplicationScoped
@Repository
internal class OrderIntentSubmissionRepository : AbstractReactiveRepository<OrderIntentSubmissionEntity, UUID>() {
    suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionEntity? {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OrderIntentSubmissionEntity o WHERE o.externalOrderId = :externalOrderId",
                OrderIntentSubmissionEntity::class.java,
            ).setParameter("externalOrderId", externalOrderId).resultList
        }.awaitSuspending().firstOrNull()
    }

    suspend fun findByStatus(status: OrderIntentSubmissionStatus): List<OrderIntentSubmissionEntity> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OrderIntentSubmissionEntity o WHERE o.status = :status ORDER BY o.submittedAt ASC",
                OrderIntentSubmissionEntity::class.java,
            ).setParameter("status", status).resultList
        }.awaitSuspending()
    }

    suspend fun update(entity: OrderIntentSubmissionEntity) {
        sessionFactory.withSession { session ->
            session.merge(entity).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

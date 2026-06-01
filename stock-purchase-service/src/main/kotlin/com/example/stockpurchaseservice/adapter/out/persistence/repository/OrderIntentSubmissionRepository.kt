package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionEntity
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
}

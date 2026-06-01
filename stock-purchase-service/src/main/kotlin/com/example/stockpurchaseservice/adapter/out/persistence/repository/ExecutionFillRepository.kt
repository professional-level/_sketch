package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.ExecutionFillEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class ExecutionFillRepository : AbstractReactiveRepository<ExecutionFillEntity, String>() {
    suspend fun exists(externalExecutionId: String): Boolean {
        return findById(externalExecutionId).awaitSuspending() != null
    }

    suspend fun sumQuantityByExternalOrderId(externalOrderId: String): Long {
        val quantity = sessionFactory.withSession { session ->
            session.createQuery(
                "SELECT COALESCE(SUM(e.quantity), 0) FROM ExecutionFillEntity e WHERE e.externalOrderId = :externalOrderId",
                java.lang.Long::class.java,
            ).setParameter("externalOrderId", externalOrderId).singleResult
        }.awaitSuspending()

        return quantity.toLong()
    }
}

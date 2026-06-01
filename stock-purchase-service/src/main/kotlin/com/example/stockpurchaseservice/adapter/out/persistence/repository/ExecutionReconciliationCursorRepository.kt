package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.ExecutionReconciliationCursorEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class ExecutionReconciliationCursorRepository :
    AbstractReactiveRepository<ExecutionReconciliationCursorEntity, String>() {

    suspend fun findAllCursors(): List<ExecutionReconciliationCursorEntity> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM ExecutionReconciliationCursorEntity c ORDER BY c.source ASC",
                ExecutionReconciliationCursorEntity::class.java,
            ).resultList
        }.awaitSuspending()
    }

    suspend fun update(cursor: ExecutionReconciliationCursorEntity) {
        sessionFactory.withSession { session ->
            session.merge(cursor).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

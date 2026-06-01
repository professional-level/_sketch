package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.UnmatchedExecutionEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class UnmatchedExecutionRepository : AbstractReactiveRepository<UnmatchedExecutionEntity, String>() {
    suspend fun exists(externalExecutionId: String): Boolean {
        return findById(externalExecutionId).awaitSuspending() != null
    }

    suspend fun countAll(): Long {
        val count = sessionFactory.withSession { session ->
            session.createQuery(
                "SELECT COUNT(e) FROM UnmatchedExecutionEntity e",
                java.lang.Long::class.java,
            ).singleResult
        }.awaitSuspending()

        return count.toLong()
    }

    suspend fun findRecent(limit: Int): List<UnmatchedExecutionEntity> {
        if (limit <= 0) return emptyList()
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM UnmatchedExecutionEntity e ORDER BY e.observedAt DESC",
                UnmatchedExecutionEntity::class.java,
            ).setMaxResults(limit).resultList
        }.awaitSuspending()
    }
}

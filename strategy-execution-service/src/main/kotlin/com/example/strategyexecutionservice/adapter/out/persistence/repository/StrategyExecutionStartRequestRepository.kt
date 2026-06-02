package com.example.strategyexecutionservice.adapter.out.persistence.repository

import com.example.strategyexecutionservice.adapter.out.persistence.entity.StrategyExecutionStartRequestEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class StrategyExecutionStartRequestRepository :
    AbstractReactiveRepository<StrategyExecutionStartRequestEntity, String>() {

    suspend fun existsByIdempotencyKey(idempotencyKey: String): Boolean {
        return findById(idempotencyKey).awaitSuspending() != null
    }

    suspend fun countAll(): Long {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "SELECT COUNT(e) FROM StrategyExecutionStartRequestEntity e",
                java.lang.Number::class.java,
            ).singleResult
        }.awaitSuspending().longValue()
    }
}

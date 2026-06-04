package com.example.strategyexecutionservice.adapter.out.persistence.repository

import com.example.strategyexecutionservice.adapter.out.persistence.entity.StrategyExecutionMilestoneEventEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class StrategyExecutionMilestoneEventRepository :
    AbstractReactiveRepository<StrategyExecutionMilestoneEventEntity, String>() {

    suspend fun existsByEventIdOrIdempotencyKey(eventId: String, idempotencyKey: String): Boolean {
        if (findById(eventId).awaitSuspending() != null) return true

        val count = sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT COUNT(e)
                FROM StrategyExecutionMilestoneEventEntity e
                WHERE e.idempotencyKey = :idempotencyKey
                """.trimIndent(),
                java.lang.Number::class.java,
            )
                .setParameter("idempotencyKey", idempotencyKey)
                .singleResult
        }.awaitSuspending()

        return count.longValue() > 0
    }
}

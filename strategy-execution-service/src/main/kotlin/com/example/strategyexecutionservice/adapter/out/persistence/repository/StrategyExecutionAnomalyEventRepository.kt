package com.example.strategyexecutionservice.adapter.out.persistence.repository

import com.example.strategyexecutionservice.adapter.out.persistence.entity.StrategyExecutionAnomalyEventEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Tuple
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class StrategyExecutionAnomalyEventRepository :
    AbstractReactiveRepository<StrategyExecutionAnomalyEventEntity, String>() {

    suspend fun existsByEventIdOrIdempotencyKey(eventId: String, idempotencyKey: String): Boolean {
        return sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT COUNT(e)
                FROM StrategyExecutionAnomalyEventEntity e
                WHERE e.eventId = :eventId
                   OR e.idempotencyKey = :idempotencyKey
                """.trimIndent(),
                java.lang.Long::class.java,
            )
                .setParameter("eventId", eventId)
                .setParameter("idempotencyKey", idempotencyKey)
                .singleResult
        }.awaitSuspending() > 0
    }

    suspend fun countByAnomalyType(): Map<String, Long> {
        val rows = sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT e.anomalyType, COUNT(e)
                FROM StrategyExecutionAnomalyEventEntity e
                GROUP BY e.anomalyType
                """.trimIndent(),
                Tuple::class.java,
            ).resultList
        }.awaitSuspending()

        return rows.associate { tuple ->
            tuple.get(0, String::class.java) to tuple.get(1, java.lang.Number::class.java).longValue()
        }
    }
}

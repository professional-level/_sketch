package com.example.strategyexecutionservice.adapter.out.persistence.repository

import com.example.strategyexecutionservice.adapter.out.persistence.entity.StrategyExecutionOrderEventEntity
import com.example.strategyexecutionservice.adapter.out.persistence.entity.StrategyExecutionOrderEventEntityType
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Tuple
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class StrategyExecutionOrderEventRepository :
    AbstractReactiveRepository<StrategyExecutionOrderEventEntity, String>() {

    suspend fun existsByEventId(eventId: String): Boolean {
        return findById(eventId).awaitSuspending() != null
    }

    suspend fun existsByEventIdOrIdempotencyKey(eventId: String, idempotencyKey: String): Boolean {
        return sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT COUNT(e)
                FROM StrategyExecutionOrderEventEntity e
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

    suspend fun countByType(): Map<StrategyExecutionOrderEventEntityType, Long> {
        val rows = sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT e.type, COUNT(e)
                FROM StrategyExecutionOrderEventEntity e
                GROUP BY e.type
                """.trimIndent(),
                Tuple::class.java,
            ).resultList
        }.awaitSuspending()

        return rows.associate { tuple ->
            tuple.get(0, StrategyExecutionOrderEventEntityType::class.java) to
                tuple.get(1, java.lang.Number::class.java).longValue()
        }
    }
}

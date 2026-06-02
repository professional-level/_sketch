package com.example.strategyexecutionservice.adapter.out.persistence.repository

import com.example.strategyexecutionservice.adapter.out.persistence.entity.LaorV4StrategyExecutionEntity
import com.example.strategyexecutionservice.adapter.out.persistence.entity.LaorV4StrategyExecutionStatus
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Tuple
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class LaorV4StrategyExecutionRepository :
    AbstractReactiveRepository<LaorV4StrategyExecutionEntity, String>() {

    suspend fun findActive(): List<LaorV4StrategyExecutionEntity> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM LaorV4StrategyExecutionEntity e WHERE e.status = :status",
                LaorV4StrategyExecutionEntity::class.java,
            )
                .setParameter("status", LaorV4StrategyExecutionStatus.ACTIVE)
                .resultList
        }.awaitSuspending()
    }

    suspend fun countByStatus(): Map<LaorV4StrategyExecutionStatus, Long> {
        val rows = sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT e.status, COUNT(e)
                FROM LaorV4StrategyExecutionEntity e
                GROUP BY e.status
                """.trimIndent(),
                Tuple::class.java,
            ).resultList
        }.awaitSuspending()

        return rows.associate { tuple ->
            tuple.get(0, LaorV4StrategyExecutionStatus::class.java) to
                tuple.get(1, java.lang.Number::class.java).longValue()
        }
    }

    suspend fun upsert(entity: LaorV4StrategyExecutionEntity) {
        sessionFactory.withSession { session ->
            session.merge(entity).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

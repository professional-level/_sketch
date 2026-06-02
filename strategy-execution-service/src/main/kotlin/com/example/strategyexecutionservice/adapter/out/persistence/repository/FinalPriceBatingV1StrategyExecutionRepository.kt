package com.example.strategyexecutionservice.adapter.out.persistence.repository

import com.example.strategyexecutionservice.adapter.out.persistence.entity.FinalPriceBatingV1StrategyExecutionEntity
import com.example.strategyexecutionservice.adapter.out.persistence.entity.FinalPriceBatingV1StrategyExecutionStatus
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Tuple
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class FinalPriceBatingV1StrategyExecutionRepository :
    AbstractReactiveRepository<FinalPriceBatingV1StrategyExecutionEntity, String>() {

    suspend fun findActive(): List<FinalPriceBatingV1StrategyExecutionEntity> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM FinalPriceBatingV1StrategyExecutionEntity e WHERE e.status = :status",
                FinalPriceBatingV1StrategyExecutionEntity::class.java,
            )
                .setParameter("status", FinalPriceBatingV1StrategyExecutionStatus.ACTIVE)
                .resultList
        }.awaitSuspending()
    }

    suspend fun countByStatus(): Map<FinalPriceBatingV1StrategyExecutionStatus, Long> {
        val rows = sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT e.status, COUNT(e)
                FROM FinalPriceBatingV1StrategyExecutionEntity e
                GROUP BY e.status
                """.trimIndent(),
                Tuple::class.java,
            ).resultList
        }.awaitSuspending()

        return rows.associate { tuple ->
            tuple.get(0, FinalPriceBatingV1StrategyExecutionStatus::class.java) to
                tuple.get(1, java.lang.Number::class.java).longValue()
        }
    }

    suspend fun upsert(entity: FinalPriceBatingV1StrategyExecutionEntity) {
        sessionFactory.withSession { session ->
            session.merge(entity).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

package com.example.strategyexecutionservice.adapter.out.persistence.repository

import com.example.strategyexecutionservice.adapter.out.persistence.entity.LaorV4StrategyExecutionEntity
import com.example.strategyexecutionservice.adapter.out.persistence.entity.LaorV4StrategyExecutionStatus
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
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

    suspend fun upsert(entity: LaorV4StrategyExecutionEntity) {
        sessionFactory.withSession { session ->
            session.merge(entity).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

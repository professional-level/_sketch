package com.example.strategyexecutionservice.adapter.out.persistence.repository

import com.example.strategyexecutionservice.adapter.out.persistence.entity.FinalPriceBatingV1StrategyExecutionEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class FinalPriceBatingV1StrategyExecutionRepository :
    AbstractReactiveRepository<FinalPriceBatingV1StrategyExecutionEntity, String>() {

    suspend fun upsert(entity: FinalPriceBatingV1StrategyExecutionEntity) {
        sessionFactory.withSession { session ->
            session.merge(entity).flatMap { session.flush() }
        }.awaitSuspending()
    }
}

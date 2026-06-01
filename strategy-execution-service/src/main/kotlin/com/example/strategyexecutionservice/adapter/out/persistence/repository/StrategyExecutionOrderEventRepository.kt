package com.example.strategyexecutionservice.adapter.out.persistence.repository

import com.example.strategyexecutionservice.adapter.out.persistence.entity.StrategyExecutionOrderEventEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class StrategyExecutionOrderEventRepository :
    AbstractReactiveRepository<StrategyExecutionOrderEventEntity, String>() {

    suspend fun existsByEventId(eventId: String): Boolean {
        return findById(eventId).awaitSuspending() != null
    }
}

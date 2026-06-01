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
}

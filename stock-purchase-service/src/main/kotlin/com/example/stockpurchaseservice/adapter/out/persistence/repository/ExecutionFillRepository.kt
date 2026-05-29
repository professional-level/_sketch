package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.ExecutionFillEntity
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
internal class ExecutionFillRepository : AbstractReactiveRepository<ExecutionFillEntity, String>() {
    suspend fun exists(externalExecutionId: String): Boolean {
        return findById(externalExecutionId).awaitSuspending() != null
    }
}

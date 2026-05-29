package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.adapter.out.persistence.entity.ExecutionFillEntity
import com.example.stockpurchaseservice.adapter.out.persistence.repository.ExecutionFillRepository
import com.example.stockpurchaseservice.application.port.out.ExecutionFillDto
import com.example.stockpurchaseservice.application.port.out.ExecutionFillPort
import io.smallrye.mutiny.coroutines.awaitSuspending

@PersistenceAdapter
internal class ExecutionFillAdapter(
    private val executionFillRepository: ExecutionFillRepository,
) : ExecutionFillPort {

    override suspend fun saveIfNew(fill: ExecutionFillDto): Boolean {
        if (executionFillRepository.exists(fill.externalExecutionId)) return false

        return runCatching {
            executionFillRepository.save(ExecutionFillEntity.from(fill)).awaitSuspending()
            true
        }.getOrDefault(false)
    }
}

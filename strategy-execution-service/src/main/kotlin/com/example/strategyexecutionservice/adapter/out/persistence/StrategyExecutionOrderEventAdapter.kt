package com.example.strategyexecutionservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.strategyexecutionservice.adapter.out.persistence.entity.StrategyExecutionOrderEventEntity
import com.example.strategyexecutionservice.adapter.out.persistence.repository.StrategyExecutionOrderEventRepository
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventRecord
import io.smallrye.mutiny.coroutines.awaitSuspending

@PersistenceAdapter
internal class StrategyExecutionOrderEventAdapter(
    private val orderEventRepository: StrategyExecutionOrderEventRepository,
) : StrategyExecutionOrderEventPort {

    override suspend fun tryRecord(event: StrategyExecutionOrderEventRecord): Boolean {
        if (orderEventRepository.existsByEventIdOrIdempotencyKey(event.eventId, event.idempotencyKey)) return false

        return runCatching {
            orderEventRepository.save(StrategyExecutionOrderEventEntity.from(event)).awaitSuspending()
            true
        }.getOrDefault(false)
    }
}

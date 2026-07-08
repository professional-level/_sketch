package com.example.strategyexecutionservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.strategyexecutionservice.adapter.out.persistence.entity.StrategyExecutionAnomalyEventEntity
import com.example.strategyexecutionservice.adapter.out.persistence.repository.StrategyExecutionAnomalyEventRepository
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionAnomalyEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionAnomalyEventRecord
import io.smallrye.mutiny.coroutines.awaitSuspending

@PersistenceAdapter
internal class StrategyExecutionAnomalyEventAdapter(
    private val anomalyEventRepository: StrategyExecutionAnomalyEventRepository,
) : StrategyExecutionAnomalyEventPort {

    override suspend fun tryRecord(event: StrategyExecutionAnomalyEventRecord): Boolean {
        if (anomalyEventRepository.existsByEventIdOrIdempotencyKey(event.eventId, event.idempotencyKey)) {
            return false
        }

        return runCatching {
            anomalyEventRepository.save(StrategyExecutionAnomalyEventEntity.from(event)).awaitSuspending()
            true
        }.getOrDefault(false)
    }
}

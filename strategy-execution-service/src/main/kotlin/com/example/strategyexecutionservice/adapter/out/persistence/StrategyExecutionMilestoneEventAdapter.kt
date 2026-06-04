package com.example.strategyexecutionservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.strategyexecutionservice.adapter.out.persistence.entity.StrategyExecutionMilestoneEventEntity
import com.example.strategyexecutionservice.adapter.out.persistence.repository.StrategyExecutionMilestoneEventRepository
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionMilestoneEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionMilestoneEventRecord
import io.smallrye.mutiny.coroutines.awaitSuspending

@PersistenceAdapter
internal class StrategyExecutionMilestoneEventAdapter(
    private val milestoneEventRepository: StrategyExecutionMilestoneEventRepository,
) : StrategyExecutionMilestoneEventPort {

    override suspend fun tryRecord(event: StrategyExecutionMilestoneEventRecord): Boolean {
        if (milestoneEventRepository.existsByEventIdOrIdempotencyKey(event.eventId, event.idempotencyKey)) {
            return false
        }

        return runCatching {
            milestoneEventRepository.save(StrategyExecutionMilestoneEventEntity.from(event)).awaitSuspending()
            true
        }.getOrDefault(false)
    }
}

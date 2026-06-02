package com.example.strategyexecutionservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.strategyexecutionservice.adapter.out.persistence.entity.FinalPriceBatingV1StrategyExecutionEntity
import com.example.strategyexecutionservice.adapter.out.persistence.entity.LaorV4StrategyExecutionEntity
import com.example.strategyexecutionservice.adapter.out.persistence.repository.FinalPriceBatingV1StrategyExecutionRepository
import com.example.strategyexecutionservice.adapter.out.persistence.entity.StrategyExecutionStartRequestEntity
import com.example.strategyexecutionservice.adapter.out.persistence.repository.LaorV4StrategyExecutionRepository
import com.example.strategyexecutionservice.adapter.out.persistence.repository.StrategyExecutionStartRequestRepository
import com.example.strategyexecutionservice.application.port.out.FinalPriceBatingV1ExecutionState
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import io.smallrye.mutiny.coroutines.awaitSuspending

@PersistenceAdapter
internal class StrategyExecutionStatePersistenceAdapter(
    private val startRequestRepository: StrategyExecutionStartRequestRepository,
    private val laorV4StrategyExecutionRepository: LaorV4StrategyExecutionRepository,
    private val finalPriceBatingV1StrategyExecutionRepository: FinalPriceBatingV1StrategyExecutionRepository,
) : StrategyExecutionStatePort {

    override suspend fun tryMarkStartRequested(idempotencyKey: String): Boolean {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        if (startRequestRepository.existsByIdempotencyKey(idempotencyKey)) return false

        return runCatching {
            startRequestRepository.save(StrategyExecutionStartRequestEntity.processed(idempotencyKey))
                .awaitSuspending()
            true
        }.getOrDefault(false)
    }

    override suspend fun findLaorV4Strategy(executionId: String): LaorV4ExecutionState? {
        return laorV4StrategyExecutionRepository.findById(executionId).awaitSuspending()?.toDto()
    }

    override suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState> {
        return laorV4StrategyExecutionRepository.findActive().map { it.toDto() }
    }

    override suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState) {
        laorV4StrategyExecutionRepository.upsert(LaorV4StrategyExecutionEntity.from(state))
    }

    override suspend fun findFinalPriceBatingV1Strategy(executionId: String): FinalPriceBatingV1ExecutionState? {
        return finalPriceBatingV1StrategyExecutionRepository.findById(executionId).awaitSuspending()?.toDto()
    }

    override suspend fun saveFinalPriceBatingV1Strategy(state: FinalPriceBatingV1ExecutionState) {
        finalPriceBatingV1StrategyExecutionRepository.upsert(FinalPriceBatingV1StrategyExecutionEntity.from(state))
    }
}

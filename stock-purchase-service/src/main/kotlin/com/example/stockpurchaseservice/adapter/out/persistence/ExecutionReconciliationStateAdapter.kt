package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.adapter.out.persistence.entity.ExecutionReconciliationCursorEntity
import com.example.stockpurchaseservice.adapter.out.persistence.entity.UnmatchedExecutionEntity
import com.example.stockpurchaseservice.adapter.out.persistence.repository.ExecutionReconciliationCursorRepository
import com.example.stockpurchaseservice.adapter.out.persistence.repository.UnmatchedExecutionRepository
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationCursorDto
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationResultDto
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationStatePort
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionDto
import io.smallrye.mutiny.coroutines.awaitSuspending
import java.time.ZonedDateTime

@PersistenceAdapter
internal class ExecutionReconciliationStateAdapter(
    private val cursorRepository: ExecutionReconciliationCursorRepository,
    private val unmatchedExecutionRepository: UnmatchedExecutionRepository,
) : ExecutionReconciliationStatePort {

    override suspend fun findCursor(source: String): ExecutionReconciliationCursorDto? {
        return cursorRepository.findById(source).awaitSuspending()?.let { cursor ->
            ExecutionReconciliationCursorDto(
                source = cursor.source,
                lastObservedExecutionId = cursor.lastObservedExecutionId,
                lastObservedExecutionAt = cursor.lastObservedExecutionAt,
            )
        }
    }

    override suspend fun markStarted(source: String, startedAt: ZonedDateTime) {
        val current = cursorRepository.findById(source).awaitSuspending()
        val next = current?.started(startedAt) ?: ExecutionReconciliationCursorEntity.started(source, startedAt)
        cursorRepository.update(next)
    }

    override suspend fun markCompleted(
        source: String,
        completedAt: ZonedDateTime,
        result: ExecutionReconciliationResultDto,
    ) {
        val current = cursorRepository.findById(source).awaitSuspending()
            ?: ExecutionReconciliationCursorEntity.started(source, completedAt)
        cursorRepository.update(current.completed(completedAt, result))
    }

    override suspend fun markFailed(source: String, failedAt: ZonedDateTime, reason: String?) {
        val current = cursorRepository.findById(source).awaitSuspending()
            ?: ExecutionReconciliationCursorEntity.started(source, failedAt)
        cursorRepository.update(current.failed(failedAt, reason))
    }

    override suspend fun saveUnmatchedExecution(execution: UnmatchedExecutionDto): Boolean {
        if (unmatchedExecutionRepository.exists(execution.externalExecutionId)) return false
        unmatchedExecutionRepository.save(UnmatchedExecutionEntity.from(execution)).awaitSuspending()
        return true
    }

    override suspend fun markUnmatchedExecutionResolved(externalExecutionId: String) {
        unmatchedExecutionRepository.deleteById(externalExecutionId).awaitSuspending()
    }
}

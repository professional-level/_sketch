package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.adapter.out.persistence.entity.ExecutionReconciliationCursorEntity
import com.example.stockpurchaseservice.adapter.out.persistence.entity.UnmatchedExecutionEntity
import com.example.stockpurchaseservice.adapter.out.persistence.entity.UnmatchedExecutionType
import com.example.stockpurchaseservice.adapter.out.persistence.repository.ExecutionReconciliationCursorRepository
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderIntentSubmissionRepository
import com.example.stockpurchaseservice.adapter.out.persistence.repository.UnmatchedExecutionRepository
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationCursorStatus
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionStatusCount
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusPort
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusSnapshot
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionStatus

@PersistenceAdapter
internal class TradingOperationsStatusAdapter(
    private val orderIntentSubmissionRepository: OrderIntentSubmissionRepository,
    private val executionReconciliationCursorRepository: ExecutionReconciliationCursorRepository,
    private val unmatchedExecutionRepository: UnmatchedExecutionRepository,
) : TradingOperationsStatusPort {

    override suspend fun loadStatus(): TradingOperationsStatusSnapshot {
        val orderCounts = orderIntentSubmissionRepository.countByStatus()
        return TradingOperationsStatusSnapshot(
            orderSubmissionStatusCounts = OrderIntentSubmissionStatusDto.values().map { status ->
                OrderSubmissionStatusCount(
                    status = status,
                    count = orderCounts.entries.firstOrNull { it.key.toDto() == status }?.value ?: 0L,
                )
            },
            reconciliationCursors = executionReconciliationCursorRepository.findAllCursors()
                .map { it.toStatus() },
            unmatchedExecutionCount = unmatchedExecutionRepository.countAll(),
            recentUnmatchedExecutions = unmatchedExecutionRepository.findRecent(RECENT_UNMATCHED_LIMIT)
                .map { it.toStatus() },
        )
    }

    private fun ExecutionReconciliationCursorEntity.toStatus(): ExecutionReconciliationCursorStatus {
        return ExecutionReconciliationCursorStatus(
            source = source,
            status = status.name,
            attemptCount = attemptCount,
            lastStartedAt = lastStartedAt,
            lastCompletedAt = lastCompletedAt,
            lastFailedAt = lastFailedAt,
            lastObservedExecutionId = lastObservedExecutionId,
            lastObservedExecutionAt = lastObservedExecutionAt,
            observedExecutionCount = observedExecutionCount,
            savedFillCount = savedFillCount,
            unmatchedExecutionCount = unmatchedExecutionCount,
            failureReason = failureReason,
        )
    }

    private fun UnmatchedExecutionEntity.toStatus(): UnmatchedExecutionStatus {
        return UnmatchedExecutionStatus(
            externalExecutionId = externalExecutionId,
            externalOrderId = externalOrderId,
            stockId = stockId,
            stockName = stockName,
            createdAt = createdAt,
            quantity = quantity,
            type = type.toDto(),
            reason = reason,
            observedAt = observedAt,
        )
    }

    private fun UnmatchedExecutionType.toDto(): ExecutionTypeDto {
        return when (this) {
            UnmatchedExecutionType.SELLING -> ExecutionTypeDto.SELLING
            UnmatchedExecutionType.PURCHASE -> ExecutionTypeDto.PURCHASE
        }
    }

    private companion object {
        const val RECENT_UNMATCHED_LIMIT = 20
    }
}

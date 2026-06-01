package com.example.stockpurchaseservice.adapter.out.persistence.entity

import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationResultDto
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "execution_reconciliation_cursor")
internal class ExecutionReconciliationCursorEntity private constructor(
    @Id
    @Column(nullable = false)
    val source: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: ExecutionReconciliationStatus,
    @Column(nullable = false)
    val attemptCount: Int,
    @Column
    val lastStartedAt: ZonedDateTime?,
    @Column
    val lastCompletedAt: ZonedDateTime?,
    @Column
    val lastFailedAt: ZonedDateTime?,
    @Column
    val lastObservedExecutionId: String?,
    @Column
    val lastObservedExecutionAt: ZonedDateTime?,
    @Column(nullable = false)
    val observedExecutionCount: Int,
    @Column(nullable = false)
    val savedFillCount: Int,
    @Column(nullable = false)
    val unmatchedExecutionCount: Int,
    @Column(length = 1000)
    val failureReason: String?,
) {
    fun started(startedAt: ZonedDateTime): ExecutionReconciliationCursorEntity {
        return ExecutionReconciliationCursorEntity(
            source = source,
            status = ExecutionReconciliationStatus.RUNNING,
            attemptCount = attemptCount + 1,
            lastStartedAt = startedAt,
            lastCompletedAt = lastCompletedAt,
            lastFailedAt = lastFailedAt,
            lastObservedExecutionId = lastObservedExecutionId,
            lastObservedExecutionAt = lastObservedExecutionAt,
            observedExecutionCount = observedExecutionCount,
            savedFillCount = savedFillCount,
            unmatchedExecutionCount = unmatchedExecutionCount,
            failureReason = null,
        )
    }

    fun completed(
        completedAt: ZonedDateTime,
        result: ExecutionReconciliationResultDto,
    ): ExecutionReconciliationCursorEntity {
        return ExecutionReconciliationCursorEntity(
            source = source,
            status = ExecutionReconciliationStatus.COMPLETED,
            attemptCount = attemptCount,
            lastStartedAt = lastStartedAt,
            lastCompletedAt = completedAt,
            lastFailedAt = lastFailedAt,
            lastObservedExecutionId = result.lastObservedExecutionId ?: lastObservedExecutionId,
            lastObservedExecutionAt = result.lastObservedExecutionAt ?: lastObservedExecutionAt,
            observedExecutionCount = result.observedExecutionCount,
            savedFillCount = result.savedFillCount,
            unmatchedExecutionCount = result.unmatchedExecutionCount,
            failureReason = null,
        )
    }

    fun failed(failedAt: ZonedDateTime, reason: String?): ExecutionReconciliationCursorEntity {
        return ExecutionReconciliationCursorEntity(
            source = source,
            status = ExecutionReconciliationStatus.FAILED,
            attemptCount = attemptCount,
            lastStartedAt = lastStartedAt,
            lastCompletedAt = lastCompletedAt,
            lastFailedAt = failedAt,
            lastObservedExecutionId = lastObservedExecutionId,
            lastObservedExecutionAt = lastObservedExecutionAt,
            observedExecutionCount = observedExecutionCount,
            savedFillCount = savedFillCount,
            unmatchedExecutionCount = unmatchedExecutionCount,
            failureReason = reason?.take(1000),
        )
    }

    companion object {
        fun started(source: String, startedAt: ZonedDateTime): ExecutionReconciliationCursorEntity {
            return ExecutionReconciliationCursorEntity(
                source = source,
                status = ExecutionReconciliationStatus.RUNNING,
                attemptCount = 1,
                lastStartedAt = startedAt,
                lastCompletedAt = null,
                lastFailedAt = null,
                lastObservedExecutionId = null,
                lastObservedExecutionAt = null,
                observedExecutionCount = 0,
                savedFillCount = 0,
                unmatchedExecutionCount = 0,
                failureReason = null,
            )
        }
    }
}

internal enum class ExecutionReconciliationStatus {
    RUNNING,
    COMPLETED,
    FAILED,
}

package com.example.stockpurchaseservice.application.port.out

import java.time.ZonedDateTime

interface ExecutionReconciliationStatePort {
    suspend fun markStarted(source: String, startedAt: ZonedDateTime)
    suspend fun markCompleted(
        source: String,
        completedAt: ZonedDateTime,
        result: ExecutionReconciliationResultDto,
    )

    suspend fun markFailed(source: String, failedAt: ZonedDateTime, reason: String?)
    suspend fun saveUnmatchedExecution(execution: UnmatchedExecutionDto)
}

data class ExecutionReconciliationResultDto(
    val lastObservedExecutionId: String?,
    val lastObservedExecutionAt: ZonedDateTime?,
    val observedExecutionCount: Int,
    val savedFillCount: Int,
    val unmatchedExecutionCount: Int,
)

data class UnmatchedExecutionDto(
    val externalExecutionId: String,
    val externalOrderId: String,
    val stockId: String,
    val stockName: String,
    val createdAt: ZonedDateTime,
    val quantity: Int,
    val type: ExecutionTypeDto,
    val reason: String,
    val observedAt: ZonedDateTime,
)

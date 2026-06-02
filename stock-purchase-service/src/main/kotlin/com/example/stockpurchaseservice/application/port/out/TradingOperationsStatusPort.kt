package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import java.time.ZonedDateTime
import java.util.UUID

interface TradingOperationsStatusPort {
    suspend fun loadStatus(): TradingOperationsStatusSnapshot
}

data class TradingOperationsStatusSnapshot(
    val orderSubmissionStatusCounts: List<OrderSubmissionStatusCount>,
    val recentProblemSubmissions: List<OrderSubmissionProblemStatus>,
    val orderExecutionOutboxStatusCounts: List<OutboxStatusCount>,
    val reconciliationCursors: List<ExecutionReconciliationCursorStatus>,
    val unmatchedExecutionCount: Long,
    val recentUnmatchedExecutions: List<UnmatchedExecutionStatus>,
)

data class OrderSubmissionStatusCount(
    val status: OrderIntentSubmissionStatusDto,
    val count: Long,
)

data class OrderSubmissionProblemStatus(
    val orderIntentId: UUID,
    val strategyExecutionId: String,
    val symbol: String,
    val market: StockOrderMarket?,
    val side: OrderIntentSide,
    val orderType: OrderIntentType,
    val status: OrderIntentSubmissionStatusDto,
    val statusReason: String?,
    val externalOrderId: String?,
    val submittedAt: ZonedDateTime,
    val lastStatusCheckedAt: ZonedDateTime?,
)

data class OutboxStatusCount(
    val status: String,
    val count: Long,
)

data class ExecutionReconciliationCursorStatus(
    val source: String,
    val status: String,
    val attemptCount: Int,
    val lastStartedAt: ZonedDateTime?,
    val lastCompletedAt: ZonedDateTime?,
    val lastFailedAt: ZonedDateTime?,
    val lastObservedExecutionId: String?,
    val lastObservedExecutionAt: ZonedDateTime?,
    val observedExecutionCount: Int,
    val savedFillCount: Int,
    val unmatchedExecutionCount: Int,
    val failureReason: String?,
)

data class UnmatchedExecutionStatus(
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

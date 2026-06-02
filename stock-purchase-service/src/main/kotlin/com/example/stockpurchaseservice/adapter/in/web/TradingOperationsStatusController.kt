package com.example.stockpurchaseservice.adapter.`in`.web

import com.example.common.WebAdapter
import com.example.stockpurchaseservice.application.port.`in`.GetTradingOperationsStatusUseCase
import com.example.stockpurchaseservice.application.port.`in`.TradingOperationsStatusResult
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationCursorStatus
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionProblemStatus
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionStatusCount
import com.example.stockpurchaseservice.application.port.out.OutboxStatusCount
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.time.Duration
import java.time.ZonedDateTime

@WebAdapter
@RequestMapping("/operations/trading")
internal class TradingOperationsStatusController(
    private val getTradingOperationsStatusUseCase: GetTradingOperationsStatusUseCase,
    @Value("\${akra.operations.trading.persistent-submission-unknown-threshold:15m}")
    private val persistentSubmissionUnknownThreshold: Duration = Duration.ofMinutes(15),
    @Value("\${akra.order.execution-reconciliation.backfill-days:7}")
    private val executionReconciliationBackfillDays: Long = 7,
) {

    @GetMapping("/status")
    suspend fun status(): TradingOperationsStatusResponse {
        return getTradingOperationsStatusUseCase.execute().toResponse(
            persistentSubmissionUnknownThreshold = persistentSubmissionUnknownThreshold,
            executionReconciliationBackfillDays = executionReconciliationBackfillDays,
        )
    }
}

internal data class TradingOperationsStatusResponse(
    val generatedAt: ZonedDateTime,
    val orderSubmissionStatusCounts: List<OrderSubmissionStatusCount>,
    val persistentSubmissionUnknownThresholdSeconds: Long,
    val recentPersistentSubmissionUnknownCount: Long,
    val recentProblemSubmissions: List<OrderSubmissionProblemStatusResponse>,
    val orderExecutionOutboxStatusCounts: List<OutboxStatusCount>,
    val executionReconciliationBackfillDays: Long,
    val reconciliationCursors: List<ExecutionReconciliationCursorStatus>,
    val unmatchedExecutionCount: Long,
    val recentUnmatchedExecutions: List<UnmatchedExecutionStatus>,
)

internal data class OrderSubmissionProblemStatusResponse(
    val orderIntentId: String,
    val strategyExecutionId: String,
    val symbol: String,
    val market: String?,
    val side: String,
    val orderType: String,
    val status: String,
    val statusReason: String?,
    val externalOrderId: String?,
    val submittedAt: ZonedDateTime,
    val lastStatusCheckedAt: ZonedDateTime?,
    val ageSeconds: Long,
    val persistentSubmissionUnknown: Boolean,
)

private fun TradingOperationsStatusResult.toResponse(
    persistentSubmissionUnknownThreshold: Duration,
    executionReconciliationBackfillDays: Long,
): TradingOperationsStatusResponse {
    val problemSubmissions = snapshot.recentProblemSubmissions.map {
        it.toResponse(generatedAt, persistentSubmissionUnknownThreshold)
    }
    return TradingOperationsStatusResponse(
        generatedAt = generatedAt,
        orderSubmissionStatusCounts = snapshot.orderSubmissionStatusCounts,
        persistentSubmissionUnknownThresholdSeconds = persistentSubmissionUnknownThreshold.seconds,
        recentPersistentSubmissionUnknownCount = problemSubmissions.count { it.persistentSubmissionUnknown }.toLong(),
        recentProblemSubmissions = problemSubmissions,
        orderExecutionOutboxStatusCounts = snapshot.orderExecutionOutboxStatusCounts,
        executionReconciliationBackfillDays = executionReconciliationBackfillDays,
        reconciliationCursors = snapshot.reconciliationCursors,
        unmatchedExecutionCount = snapshot.unmatchedExecutionCount,
        recentUnmatchedExecutions = snapshot.recentUnmatchedExecutions,
    )
}

private fun OrderSubmissionProblemStatus.toResponse(
    generatedAt: ZonedDateTime,
    persistentSubmissionUnknownThreshold: Duration,
): OrderSubmissionProblemStatusResponse {
    val ageSeconds = Duration.between(submittedAt, generatedAt).seconds.coerceAtLeast(0)
    return OrderSubmissionProblemStatusResponse(
        orderIntentId = orderIntentId.toString(),
        strategyExecutionId = strategyExecutionId,
        symbol = symbol,
        market = market?.name,
        side = side.name,
        orderType = orderType.name,
        status = status.name,
        statusReason = statusReason,
        externalOrderId = externalOrderId,
        submittedAt = submittedAt,
        lastStatusCheckedAt = lastStatusCheckedAt,
        ageSeconds = ageSeconds,
        persistentSubmissionUnknown = status == OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN &&
            ageSeconds >= persistentSubmissionUnknownThreshold.seconds,
    )
}

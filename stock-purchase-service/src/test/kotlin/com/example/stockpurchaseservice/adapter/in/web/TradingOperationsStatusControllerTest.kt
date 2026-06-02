package com.example.stockpurchaseservice.adapter.`in`.web

import com.example.stockpurchaseservice.application.port.`in`.GetTradingOperationsStatusUseCase
import com.example.stockpurchaseservice.application.port.`in`.TradingOperationsStatusResult
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationCursorStatus
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionStatusCount
import com.example.stockpurchaseservice.application.port.out.OutboxStatusCount
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusSnapshot
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionStatus
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class TradingOperationsStatusControllerTest {

    @Test
    fun `returns trading operations status response`() = runBlocking {
        val result = TradingOperationsStatusResult(
            generatedAt = ZonedDateTime.parse("2026-06-02T10:00:00+09:00[Asia/Seoul]"),
            snapshot = snapshot(),
        )
        val useCase = FakeGetTradingOperationsStatusUseCase(result)
        val controller = TradingOperationsStatusController(useCase)

        val response = controller.status()

        assertEquals(1, useCase.callCount)
        assertEquals(result.generatedAt, response.generatedAt)
        assertEquals(result.snapshot.orderSubmissionStatusCounts, response.orderSubmissionStatusCounts)
        assertEquals(result.snapshot.orderExecutionOutboxStatusCounts, response.orderExecutionOutboxStatusCounts)
        assertEquals(result.snapshot.reconciliationCursors, response.reconciliationCursors)
        assertEquals(result.snapshot.unmatchedExecutionCount, response.unmatchedExecutionCount)
        assertEquals(result.snapshot.recentUnmatchedExecutions, response.recentUnmatchedExecutions)
    }

    private fun snapshot(): TradingOperationsStatusSnapshot {
        return TradingOperationsStatusSnapshot(
            orderSubmissionStatusCounts = listOf(
                OrderSubmissionStatusCount(OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN, 1),
            ),
            orderExecutionOutboxStatusCounts = listOf(
                OutboxStatusCount("FAILED", 2),
                OutboxStatusCount("PENDING", 5),
            ),
            reconciliationCursors = listOf(
                ExecutionReconciliationCursorStatus(
                    source = "kis:overseas",
                    status = "FAILED",
                    attemptCount = 3,
                    lastStartedAt = ZonedDateTime.parse("2026-06-02T09:58:00+09:00[Asia/Seoul]"),
                    lastCompletedAt = null,
                    lastFailedAt = ZonedDateTime.parse("2026-06-02T09:59:00+09:00[Asia/Seoul]"),
                    lastObservedExecutionId = "exec-0",
                    lastObservedExecutionAt = ZonedDateTime.parse("2026-06-01T16:00:00-04:00[America/New_York]"),
                    observedExecutionCount = 12,
                    savedFillCount = 11,
                    unmatchedExecutionCount = 1,
                    failureReason = "broker timeout",
                ),
            ),
            unmatchedExecutionCount = 1,
            recentUnmatchedExecutions = listOf(
                UnmatchedExecutionStatus(
                    externalExecutionId = "exec-1",
                    externalOrderId = "order-1",
                    stockId = "TQQQ",
                    stockName = "ProShares UltraPro QQQ",
                    createdAt = ZonedDateTime.parse("2026-06-01T16:00:00-04:00[America/New_York]"),
                    quantity = 1,
                    type = ExecutionTypeDto.PURCHASE,
                    reason = "no matching order submission",
                    observedAt = ZonedDateTime.parse("2026-06-02T10:00:00+09:00[Asia/Seoul]"),
                ),
            ),
        )
    }

    private class FakeGetTradingOperationsStatusUseCase(
        private val result: TradingOperationsStatusResult,
    ) : GetTradingOperationsStatusUseCase {
        var callCount: Int = 0

        override suspend fun execute(): TradingOperationsStatusResult {
            callCount += 1
            return result
        }
    }
}

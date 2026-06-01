package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionStatusCount
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusPort
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusSnapshot
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GetTradingOperationsStatusServiceTest {

    @Test
    fun `returns generated time and trading operations snapshot`() = runBlocking {
        val snapshot = snapshot()
        val service = GetTradingOperationsStatusService(FakeTradingOperationsStatusPort(snapshot)).apply {
            clock = Clock.fixed(
                Instant.parse("2026-06-02T01:00:00Z"),
                ZoneId.of("Asia/Seoul"),
            )
        }

        val result = service.execute()

        assertEquals(ZonedDateTime.parse("2026-06-02T10:00:00+09:00[Asia/Seoul]"), result.generatedAt)
        assertEquals(snapshot, result.snapshot)
    }

    private fun snapshot(): TradingOperationsStatusSnapshot {
        return TradingOperationsStatusSnapshot(
            orderSubmissionStatusCounts = listOf(
                OrderSubmissionStatusCount(OrderIntentSubmissionStatusDto.SUBMITTED, 2),
            ),
            reconciliationCursors = emptyList(),
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

    private class FakeTradingOperationsStatusPort(
        private val snapshot: TradingOperationsStatusSnapshot,
    ) : TradingOperationsStatusPort {
        override suspend fun loadStatus(): TradingOperationsStatusSnapshot {
            return snapshot
        }
    }
}

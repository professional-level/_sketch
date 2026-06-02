package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionProblemStatus
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionStatusCount
import com.example.stockpurchaseservice.application.port.out.OutboxStatusCount
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusPort
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusSnapshot
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
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
            recentProblemSubmissions = listOf(
                OrderSubmissionProblemStatus(
                    orderIntentId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    strategyExecutionId = "laor-v4:TQQQ",
                    symbol = "TQQQ",
                    market = StockOrderMarket.OVERSEAS_US,
                    side = OrderIntentSide.BUY,
                    orderType = OrderIntentType.LOC,
                    status = OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN,
                    statusReason = "broker status lookup failed",
                    externalOrderId = "broker-1",
                    submittedAt = ZonedDateTime.parse("2026-06-02T09:30:00+09:00[Asia/Seoul]"),
                    lastStatusCheckedAt = ZonedDateTime.parse("2026-06-02T09:31:00+09:00[Asia/Seoul]"),
                ),
            ),
            orderExecutionOutboxStatusCounts = listOf(
                OutboxStatusCount("PENDING", 1),
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

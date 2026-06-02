package com.example.stockpurchaseservice.adapter.out.observability

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationCursorStatus
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionProblemStatus
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionStatusCount
import com.example.stockpurchaseservice.application.port.out.OutboxStatusCount
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusPort
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusSnapshot
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionStatus
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class TradingOperationsMetricsAdapterTest {

    @Test
    fun `records trading operations snapshot gauges`() = runBlocking {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = TradingOperationsMetricsAdapter(
            tradingOperationsStatusPort = FakeTradingOperationsStatusPort(snapshot()),
            meterRegistry = meterRegistry,
        )

        adapter.refresh()

        assertEquals(
            3.0,
            meterRegistry.gaugeValue(
                TradingOperationsMetricsAdapter.ORDER_SUBMISSION_STATUS_GAUGE,
                "status",
                "SUBMISSION_UNKNOWN",
            ),
        )
        assertEquals(
            2.0,
            meterRegistry.gaugeValue(
                TradingOperationsMetricsAdapter.PROBLEM_SUBMISSION_GAUGE,
                "status",
                "SUBMISSION_UNKNOWN",
            ),
        )
        assertEquals(
            5.0,
            meterRegistry.gaugeValue(
                TradingOperationsMetricsAdapter.ORDER_EXECUTION_OUTBOX_GAUGE,
                "status",
                "FAILED",
            ),
        )
        assertEquals(
            1.0,
            meterRegistry.gaugeValue(
                TradingOperationsMetricsAdapter.RECONCILIATION_CURSOR_FAILURE_GAUGE,
                "source",
                "kis:overseas",
            ),
        )
        assertEquals(
            4.0,
            meterRegistry.gaugeValue(
                TradingOperationsMetricsAdapter.RECONCILIATION_CURSOR_UNMATCHED_EXECUTION_GAUGE,
                "source",
                "kis:overseas",
            ),
        )
        assertEquals(
            7.0,
            meterRegistry.gaugeValue(TradingOperationsMetricsAdapter.UNMATCHED_EXECUTION_GAUGE),
        )
    }

    @Test
    fun `resets stale problem and cursor gauges on next snapshot`() = runBlocking {
        val meterRegistry = SimpleMeterRegistry()
        val statusPort = FakeTradingOperationsStatusPort(snapshot())
        val adapter = TradingOperationsMetricsAdapter(statusPort, meterRegistry)

        adapter.refresh()
        statusPort.snapshot = snapshot(
            recentProblemSubmissions = emptyList(),
            reconciliationCursors = listOf(
                reconciliationCursor(
                    status = "COMPLETED",
                    unmatchedExecutionCount = 0,
                ),
            ),
            unmatchedExecutionCount = 0,
        )
        adapter.refresh()

        assertEquals(
            0.0,
            meterRegistry.gaugeValue(
                TradingOperationsMetricsAdapter.PROBLEM_SUBMISSION_GAUGE,
                "status",
                "SUBMISSION_UNKNOWN",
            ),
        )
        assertEquals(
            0.0,
            meterRegistry.gaugeValue(
                TradingOperationsMetricsAdapter.RECONCILIATION_CURSOR_FAILURE_GAUGE,
                "source",
                "kis:overseas",
            ),
        )
        assertEquals(
            0.0,
            meterRegistry.gaugeValue(
                TradingOperationsMetricsAdapter.RECONCILIATION_CURSOR_UNMATCHED_EXECUTION_GAUGE,
                "source",
                "kis:overseas",
            ),
        )
    }

    @Test
    fun `records refresh failure counter without throwing`() = runBlocking {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = TradingOperationsMetricsAdapter(
            tradingOperationsStatusPort = FakeTradingOperationsStatusPort(
                snapshot = snapshot(),
                failure = IllegalStateException("database unavailable"),
            ),
            meterRegistry = meterRegistry,
        )

        adapter.refresh()

        assertEquals(
            1.0,
            meterRegistry.counter(TradingOperationsMetricsAdapter.METRICS_REFRESH_FAILURE_COUNTER).count(),
        )
    }

    private fun SimpleMeterRegistry.gaugeValue(
        name: String,
        vararg tags: String,
    ): Double {
        return find(name)
            .tags(*tags)
            .gauge()
            ?.value()
            ?: 0.0
    }

    private fun snapshot(
        recentProblemSubmissions: List<OrderSubmissionProblemStatus> = listOf(problemSubmission(), problemSubmission()),
        reconciliationCursors: List<ExecutionReconciliationCursorStatus> = listOf(reconciliationCursor()),
        unmatchedExecutionCount: Long = 7,
    ): TradingOperationsStatusSnapshot {
        return TradingOperationsStatusSnapshot(
            orderSubmissionStatusCounts = listOf(
                OrderSubmissionStatusCount(OrderIntentSubmissionStatusDto.SUBMITTED, 11),
                OrderSubmissionStatusCount(OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN, 3),
            ),
            recentProblemSubmissions = recentProblemSubmissions,
            orderExecutionOutboxStatusCounts = listOf(
                OutboxStatusCount("PENDING", 2),
                OutboxStatusCount("FAILED", 5),
            ),
            reconciliationCursors = reconciliationCursors,
            unmatchedExecutionCount = unmatchedExecutionCount,
            recentUnmatchedExecutions = listOf(
                UnmatchedExecutionStatus(
                    externalExecutionId = "exec-1",
                    externalOrderId = "order-1",
                    stockId = "TQQQ",
                    stockName = "ProShares UltraPro QQQ",
                    createdAt = OBSERVED_AT.minusMinutes(1),
                    quantity = 1,
                    type = ExecutionTypeDto.PURCHASE,
                    reason = "no matching order submission",
                    observedAt = OBSERVED_AT,
                ),
            ),
        )
    }

    private fun problemSubmission(): OrderSubmissionProblemStatus {
        return OrderSubmissionProblemStatus(
            orderIntentId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            strategyExecutionId = "laor-v4:TQQQ",
            symbol = "TQQQ",
            market = StockOrderMarket.OVERSEAS_US,
            side = OrderIntentSide.BUY,
            orderType = OrderIntentType.LOC,
            status = OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN,
            statusReason = "broker status lookup failed",
            externalOrderId = "broker-1",
            submittedAt = OBSERVED_AT.minusMinutes(20),
            lastStatusCheckedAt = OBSERVED_AT.minusMinutes(1),
        )
    }

    private fun reconciliationCursor(
        status: String = "FAILED",
        unmatchedExecutionCount: Int = 4,
    ): ExecutionReconciliationCursorStatus {
        return ExecutionReconciliationCursorStatus(
            source = "kis:overseas",
            status = status,
            attemptCount = 3,
            lastStartedAt = OBSERVED_AT.minusMinutes(2),
            lastCompletedAt = null,
            lastFailedAt = OBSERVED_AT.minusMinutes(1),
            lastObservedExecutionId = "exec-0",
            lastObservedExecutionAt = OBSERVED_AT.minusHours(1),
            observedExecutionCount = 10,
            savedFillCount = 6,
            unmatchedExecutionCount = unmatchedExecutionCount,
            failureReason = if (status == "FAILED") "broker timeout" else null,
        )
    }

    private class FakeTradingOperationsStatusPort(
        var snapshot: TradingOperationsStatusSnapshot,
        private val failure: RuntimeException? = null,
    ) : TradingOperationsStatusPort {

        override suspend fun loadStatus(): TradingOperationsStatusSnapshot {
            failure?.let { throw it }
            return snapshot
        }
    }

    private companion object {
        val OBSERVED_AT: ZonedDateTime = ZonedDateTime.parse("2026-06-02T10:00:00+09:00[Asia/Seoul]")
    }
}

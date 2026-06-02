package com.example.stockpurchaseservice.adapter.out.observability

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationCursorStatus
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionStatusCount
import com.example.stockpurchaseservice.application.port.out.OutboxStatusCount
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusPort
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusSnapshot
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled

@ExternalApiAdapter
internal class TradingOperationsMetricsAdapter(
    private val tradingOperationsStatusPort: TradingOperationsStatusPort,
    private val meterRegistry: MeterRegistry,
    @Value("\${akra.operations.metrics.enabled:true}")
    private val enabled: Boolean = true,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val orderSubmissionStatusGauges = ConcurrentHashMap<String, AtomicLong>()
    private val problemSubmissionStatusGauges = ConcurrentHashMap<String, AtomicLong>()
    private val outboxStatusGauges = ConcurrentHashMap<String, AtomicLong>()
    private val reconciliationCursorFailureGauges = ConcurrentHashMap<String, AtomicLong>()
    private val reconciliationCursorUnmatchedExecutionGauges = ConcurrentHashMap<String, AtomicLong>()
    private val unmatchedExecutionGauge = registerGauge(
        name = UNMATCHED_EXECUTION_GAUGE,
        description = "Total unmatched broker executions observed by reconciliation",
        tags = emptyList(),
    )

    @Scheduled(
        initialDelayString = "\${akra.operations.metrics.refresh-initial-delay-ms:5000}",
        fixedDelayString = "\${akra.operations.metrics.refresh-fixed-delay-ms:30000}",
    )
    suspend fun refresh() {
        if (!enabled) {
            return
        }
        runCatching {
            tradingOperationsStatusPort.loadStatus()
        }.onSuccess { snapshot ->
            recordSnapshot(snapshot)
        }.onFailure { exception ->
            recordRefreshFailure(exception)
        }
    }

    internal fun recordSnapshot(snapshot: TradingOperationsStatusSnapshot) {
        resetGauges(problemSubmissionStatusGauges)
        resetGauges(reconciliationCursorFailureGauges)
        resetGauges(reconciliationCursorUnmatchedExecutionGauges)

        snapshot.orderSubmissionStatusCounts.forEach { count ->
            recordOrderSubmissionStatus(count)
        }
        snapshot.recentProblemSubmissions
            .groupingBy { it.status.name }
            .eachCount()
            .forEach { (status, count) ->
                updateGauge(
                    gauges = problemSubmissionStatusGauges,
                    key = status,
                    name = PROBLEM_SUBMISSION_GAUGE,
                    description = "Recent problematic order submissions by status",
                    value = count.toLong(),
                    tags = listOf("status", status),
                )
            }
        snapshot.orderExecutionOutboxStatusCounts.forEach { count ->
            recordOutboxStatus(count)
        }
        snapshot.reconciliationCursors.forEach { cursor ->
            recordReconciliationCursor(cursor)
        }
        unmatchedExecutionGauge.set(snapshot.unmatchedExecutionCount)
    }

    private fun recordOrderSubmissionStatus(count: OrderSubmissionStatusCount) {
        updateGauge(
            gauges = orderSubmissionStatusGauges,
            key = count.status.name,
            name = ORDER_SUBMISSION_STATUS_GAUGE,
            description = "Order intent submissions by status",
            value = count.count,
            tags = listOf("status", count.status.name),
        )
    }

    private fun recordOutboxStatus(count: OutboxStatusCount) {
        updateGauge(
            gauges = outboxStatusGauges,
            key = count.status,
            name = ORDER_EXECUTION_OUTBOX_GAUGE,
            description = "Order execution outbox rows by status",
            value = count.count,
            tags = listOf("status", count.status),
        )
    }

    private fun recordReconciliationCursor(cursor: ExecutionReconciliationCursorStatus) {
        updateGauge(
            gauges = reconciliationCursorFailureGauges,
            key = cursor.source,
            name = RECONCILIATION_CURSOR_FAILURE_GAUGE,
            description = "Failed reconciliation cursor marker by source",
            value = if (cursor.status == "FAILED") 1L else 0L,
            tags = listOf("source", cursor.source),
        )
        updateGauge(
            gauges = reconciliationCursorUnmatchedExecutionGauges,
            key = cursor.source,
            name = RECONCILIATION_CURSOR_UNMATCHED_EXECUTION_GAUGE,
            description = "Unmatched executions observed by reconciliation cursor source",
            value = cursor.unmatchedExecutionCount.toLong(),
            tags = listOf("source", cursor.source),
        )
    }

    private fun updateGauge(
        gauges: ConcurrentHashMap<String, AtomicLong>,
        key: String,
        name: String,
        description: String,
        value: Long,
        tags: List<String>,
    ) {
        val gauge = gauges.computeIfAbsent(key) {
            registerGauge(
                name = name,
                description = description,
                tags = tags,
            )
        }
        gauge.set(value)
    }

    private fun registerGauge(
        name: String,
        description: String,
        tags: List<String>,
    ): AtomicLong {
        val value = AtomicLong(0)
        Gauge.builder(name, value) { it.get().toDouble() }
            .description(description)
            .tags(*tags.toTypedArray())
            .register(meterRegistry)
        return value
    }

    private fun resetGauges(gauges: ConcurrentHashMap<String, AtomicLong>) {
        gauges.values.forEach { it.set(0) }
    }

    private fun recordRefreshFailure(exception: Throwable) {
        Counter.builder(METRICS_REFRESH_FAILURE_COUNTER)
            .description("Failures while refreshing stock-purchase-service operations metrics")
            .register(meterRegistry)
            .increment()
        log.warn("Failed to refresh trading operations metrics", exception)
    }

    companion object {
        internal const val ORDER_SUBMISSION_STATUS_GAUGE = "stock.purchase.order.submissions"
        internal const val PROBLEM_SUBMISSION_GAUGE = "stock.purchase.order.problem.submissions"
        internal const val ORDER_EXECUTION_OUTBOX_GAUGE = "stock.purchase.order.execution.outbox.events"
        internal const val UNMATCHED_EXECUTION_GAUGE = "stock.purchase.reconciliation.unmatched.executions"
        internal const val RECONCILIATION_CURSOR_FAILURE_GAUGE =
            "stock.purchase.reconciliation.cursor.failures"
        internal const val RECONCILIATION_CURSOR_UNMATCHED_EXECUTION_GAUGE =
            "stock.purchase.reconciliation.cursor.unmatched.executions"
        internal const val METRICS_REFRESH_FAILURE_COUNTER = "stock.purchase.operations.metrics.refresh.failures"
    }
}

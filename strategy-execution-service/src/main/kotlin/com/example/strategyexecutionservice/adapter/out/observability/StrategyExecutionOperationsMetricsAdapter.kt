package com.example.strategyexecutionservice.adapter.out.observability

import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusSnapshot
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatusCount
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled

@ExternalApiAdapter
internal class StrategyExecutionOperationsMetricsAdapter(
    private val strategyExecutionOperationsStatusPort: StrategyExecutionOperationsStatusPort,
    private val meterRegistry: MeterRegistry,
    @Value("\${akra.strategy-execution.operations.metrics.enabled:true}")
    private val enabled: Boolean = true,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val orderIntentOutboxStatusGauges = ConcurrentHashMap<String, AtomicLong>()
    private val orderEventTypeGauges = ConcurrentHashMap<String, AtomicLong>()
    private val laorV4StatusGauges = ConcurrentHashMap<String, AtomicLong>()
    private val finalPriceBatingV1StatusGauges = ConcurrentHashMap<String, AtomicLong>()
    private val strategyExecutionStartRequestGauge = registerGauge(
        name = STRATEGY_EXECUTION_START_REQUEST_GAUGE,
        description = "Total strategy execution start requests",
        tags = emptyList(),
    )

    @Scheduled(
        initialDelayString = "\${akra.strategy-execution.operations.metrics.refresh-initial-delay-ms:5000}",
        fixedDelayString = "\${akra.strategy-execution.operations.metrics.refresh-fixed-delay-ms:30000}",
    )
    suspend fun refresh() {
        if (!enabled) {
            return
        }
        runCatching {
            strategyExecutionOperationsStatusPort.loadStatus()
        }.onSuccess { snapshot ->
            recordSnapshot(snapshot)
        }.onFailure { exception ->
            recordRefreshFailure(exception)
        }
    }

    internal fun recordSnapshot(snapshot: StrategyExecutionOperationsStatusSnapshot) {
        resetGauges(orderIntentOutboxStatusGauges)
        resetGauges(orderEventTypeGauges)
        resetGauges(laorV4StatusGauges)
        resetGauges(finalPriceBatingV1StatusGauges)

        snapshot.orderIntentOutboxStatusCounts.forEach { count ->
            recordStatusCount(
                gauges = orderIntentOutboxStatusGauges,
                count = count,
                name = ORDER_INTENT_OUTBOX_GAUGE,
                description = "Order intent outbox rows by status",
            )
        }
        strategyExecutionStartRequestGauge.set(snapshot.strategyExecutionStartRequestCount)
        snapshot.strategyExecutionOrderEventTypeCounts.forEach { count ->
            recordStatusCount(
                gauges = orderEventTypeGauges,
                count = count,
                name = STRATEGY_EXECUTION_ORDER_EVENT_TYPE_GAUGE,
                description = "Strategy execution order events by type",
            )
        }
        snapshot.laorV4StatusCounts.forEach { count ->
            recordStatusCount(
                gauges = laorV4StatusGauges,
                count = count,
                name = LAOR_V4_STATUS_GAUGE,
                description = "LAOR v4 strategy executions by status",
            )
        }
        snapshot.finalPriceBatingV1StatusCounts.forEach { count ->
            recordStatusCount(
                gauges = finalPriceBatingV1StatusGauges,
                count = count,
                name = FINAL_PRICE_BATING_V1_STATUS_GAUGE,
                description = "FinalPriceBating v1 strategy executions by status",
            )
        }
    }

    private fun recordStatusCount(
        gauges: ConcurrentHashMap<String, AtomicLong>,
        count: StrategyExecutionStatusCount,
        name: String,
        description: String,
    ) {
        val gauge = gauges.computeIfAbsent(count.status) {
            registerGauge(
                name = name,
                description = description,
                tags = listOf("status", count.status),
            )
        }
        gauge.set(count.count)
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
            .description("Failures while refreshing strategy-execution-service operations metrics")
            .register(meterRegistry)
            .increment()
        log.warn("Failed to refresh strategy execution operations metrics", exception)
    }

    companion object {
        internal const val ORDER_INTENT_OUTBOX_GAUGE = "strategy.execution.order.intent.outbox.events"
        internal const val STRATEGY_EXECUTION_START_REQUEST_GAUGE =
            "strategy.execution.start.requests"
        internal const val STRATEGY_EXECUTION_ORDER_EVENT_TYPE_GAUGE =
            "strategy.execution.order.events"
        internal const val LAOR_V4_STATUS_GAUGE = "strategy.execution.laor.v4.executions"
        internal const val FINAL_PRICE_BATING_V1_STATUS_GAUGE =
            "strategy.execution.final.price.bating.v1.executions"
        internal const val METRICS_REFRESH_FAILURE_COUNTER =
            "strategy.execution.operations.metrics.refresh.failures"
    }
}

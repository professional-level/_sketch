package com.example.strategyexecutionservice.adapter.out.observability

import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusSnapshot
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatusCount
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyExecutionOperationsMetricsAdapterTest {

    @Test
    fun `records strategy execution operations snapshot gauges`() = runBlocking {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = StrategyExecutionOperationsMetricsAdapter(
            strategyExecutionOperationsStatusPort = FakeStrategyExecutionOperationsStatusPort(snapshot()),
            meterRegistry = meterRegistry,
        )

        adapter.refresh()

        assertEquals(
            5.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.ORDER_INTENT_OUTBOX_GAUGE,
                "status",
                "FAILED",
            ),
        )
        assertEquals(
            7.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.STRATEGY_EXECUTION_START_REQUEST_GAUGE,
            ),
        )
        assertEquals(
            4.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.STRATEGY_EXECUTION_ORDER_EVENT_TYPE_GAUGE,
                "status",
                "FILLED",
            ),
        )
        assertEquals(
            2.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.LAOR_V4_STATUS_GAUGE,
                "status",
                "ACTIVE",
            ),
        )
        assertEquals(
            3.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.FINAL_PRICE_BATING_V1_STATUS_GAUGE,
                "status",
                "COMPLETED",
            ),
        )
        assertEquals(
            6.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.LAOR_ORDER_ANOMALY_TYPE_GAUGE,
                "status",
                "FILL_BEFORE_SUBMIT",
            ),
        )
    }

    @Test
    fun `resets stale strategy execution gauges on next snapshot`() = runBlocking {
        val meterRegistry = SimpleMeterRegistry()
        val statusPort = FakeStrategyExecutionOperationsStatusPort(snapshot())
        val adapter = StrategyExecutionOperationsMetricsAdapter(statusPort, meterRegistry)

        adapter.refresh()
        statusPort.snapshot = snapshot(
            orderIntentOutboxStatusCounts = listOf(StrategyExecutionStatusCount("PENDING", 1)),
            strategyExecutionStartRequestCount = 1,
            strategyExecutionOrderEventTypeCounts = listOf(StrategyExecutionStatusCount("SUBMITTED", 1)),
            laorV4StatusCounts = listOf(StrategyExecutionStatusCount("COMPLETED", 1)),
            finalPriceBatingV1StatusCounts = listOf(StrategyExecutionStatusCount("ACTIVE", 1)),
            laorOrderAnomalyTypeCounts = listOf(StrategyExecutionStatusCount("UNKNOWN_ORDER_INTENT", 1)),
        )
        adapter.refresh()

        assertEquals(
            0.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.ORDER_INTENT_OUTBOX_GAUGE,
                "status",
                "FAILED",
            ),
        )
        assertEquals(
            1.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.STRATEGY_EXECUTION_START_REQUEST_GAUGE,
            ),
        )
        assertEquals(
            0.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.STRATEGY_EXECUTION_ORDER_EVENT_TYPE_GAUGE,
                "status",
                "FILLED",
            ),
        )
        assertEquals(
            0.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.LAOR_V4_STATUS_GAUGE,
                "status",
                "ACTIVE",
            ),
        )
        assertEquals(
            0.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.FINAL_PRICE_BATING_V1_STATUS_GAUGE,
                "status",
                "COMPLETED",
            ),
        )
        assertEquals(
            0.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.LAOR_ORDER_ANOMALY_TYPE_GAUGE,
                "status",
                "FILL_BEFORE_SUBMIT",
            ),
        )
        assertEquals(
            1.0,
            meterRegistry.gaugeValue(
                StrategyExecutionOperationsMetricsAdapter.LAOR_ORDER_ANOMALY_TYPE_GAUGE,
                "status",
                "UNKNOWN_ORDER_INTENT",
            ),
        )
    }

    @Test
    fun `records refresh failure counter without throwing`() = runBlocking {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = StrategyExecutionOperationsMetricsAdapter(
            strategyExecutionOperationsStatusPort = FakeStrategyExecutionOperationsStatusPort(
                snapshot = snapshot(),
                failure = IllegalStateException("database unavailable"),
            ),
            meterRegistry = meterRegistry,
        )

        adapter.refresh()

        assertEquals(
            1.0,
            meterRegistry.counter(StrategyExecutionOperationsMetricsAdapter.METRICS_REFRESH_FAILURE_COUNTER).count(),
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
        orderIntentOutboxStatusCounts: List<StrategyExecutionStatusCount> = listOf(
            StrategyExecutionStatusCount("PENDING", 2),
            StrategyExecutionStatusCount("FAILED", 5),
        ),
        strategyExecutionStartRequestCount: Long = 7,
        strategyExecutionOrderEventTypeCounts: List<StrategyExecutionStatusCount> = listOf(
            StrategyExecutionStatusCount("SUBMITTED", 3),
            StrategyExecutionStatusCount("FILLED", 4),
        ),
        laorV4StatusCounts: List<StrategyExecutionStatusCount> = listOf(
            StrategyExecutionStatusCount("ACTIVE", 2),
            StrategyExecutionStatusCount("COMPLETED", 8),
        ),
        finalPriceBatingV1StatusCounts: List<StrategyExecutionStatusCount> = listOf(
            StrategyExecutionStatusCount("ACTIVE", 1),
            StrategyExecutionStatusCount("COMPLETED", 3),
        ),
        laorOrderAnomalyTypeCounts: List<StrategyExecutionStatusCount> = listOf(
            StrategyExecutionStatusCount("FILL_BEFORE_SUBMIT", 6),
        ),
    ): StrategyExecutionOperationsStatusSnapshot {
        return StrategyExecutionOperationsStatusSnapshot(
            orderIntentOutboxStatusCounts = orderIntentOutboxStatusCounts,
            strategyExecutionStartRequestCount = strategyExecutionStartRequestCount,
            strategyExecutionOrderEventTypeCounts = strategyExecutionOrderEventTypeCounts,
            laorV4StatusCounts = laorV4StatusCounts,
            finalPriceBatingV1StatusCounts = finalPriceBatingV1StatusCounts,
            laorOrderAnomalyTypeCounts = laorOrderAnomalyTypeCounts,
        )
    }

    private class FakeStrategyExecutionOperationsStatusPort(
        var snapshot: StrategyExecutionOperationsStatusSnapshot,
        private val failure: RuntimeException? = null,
    ) : StrategyExecutionOperationsStatusPort {

        override suspend fun loadStatus(): StrategyExecutionOperationsStatusSnapshot {
            failure?.let { throw it }
            return snapshot
        }
    }
}

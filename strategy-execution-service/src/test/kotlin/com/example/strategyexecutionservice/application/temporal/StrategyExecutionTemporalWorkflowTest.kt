package com.example.strategyexecutionservice.application.temporal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrategyExecutionTemporalWorkflowTest {

    @Test
    fun `creates deterministic W3C trace context from workflow identity`() {
        val context = temporalTraceContext(
            workflowType = RUN_ACTIVE_STRATEGY_EXECUTIONS_WORKFLOW_TYPE,
            workflowId = "strategy-execution-active-daily",
            runId = "00000000-0000-0000-0000-000000000001",
        )

        val repeated = temporalTraceContext(
            workflowType = RUN_ACTIVE_STRATEGY_EXECUTIONS_WORKFLOW_TYPE,
            workflowId = "strategy-execution-active-daily",
            runId = "00000000-0000-0000-0000-000000000001",
        )

        assertEquals(repeated, context)
        assertEquals(32, context.traceId.length)
        assertEquals(16, context.spanId.length)
        assertEquals("00-${context.traceId}-${context.spanId}-01", context.traceParent)
        assertTrue(context.traceId.matches(Regex("[0-9a-f]{32}")))
        assertTrue(context.spanId.matches(Regex("[0-9a-f]{16}")))
        assertFalse(context.isEmpty())
    }
}

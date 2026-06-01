package common.observability

import kotlinx.coroutines.runBlocking
import org.slf4j.MDC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TraceContextTest {

    @Test
    fun `builds current trace context from MDC values`() {
        MDC.put("traceId", TRACE_ID)
        MDC.put("spanId", SPAN_ID)
        try {
            val context = TraceContext.current()

            assertEquals(TRACE_ID, context.traceId)
            assertEquals(SPAN_ID, context.spanId)
            assertEquals(TRACE_PARENT, context.traceParent)
        } finally {
            MDC.clear()
        }
    }

    @Test
    fun `uses explicit traceparent from headers`() {
        val context = TraceContext.fromHeaders(
            traceParent = TRACE_PARENT,
            traceId = "ignored-trace-id",
            spanId = "ignored-span-id",
        )

        assertEquals("ignored-trace-id", context.traceId)
        assertEquals("ignored-span-id", context.spanId)
        assertEquals(TRACE_PARENT, context.traceParent)
    }

    @Test
    fun `sets and restores MDC around suspending block`() = runBlocking {
        MDC.put("traceId", "previous-trace")
        val context = TraceContext(traceId = TRACE_ID, spanId = SPAN_ID, traceParent = TRACE_PARENT)

        val observed = context.withMdc {
            Triple(
                MDC.get("traceId"),
                MDC.get("spanId"),
                MDC.get("traceparent"),
            )
        }

        assertEquals(Triple(TRACE_ID, SPAN_ID, TRACE_PARENT), observed)
        assertEquals("previous-trace", MDC.get("traceId"))
        assertNull(MDC.get("spanId"))
        assertNull(MDC.get("traceparent"))
        MDC.clear()
    }

    private companion object {
        const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        const val SPAN_ID = "00f067aa0ba902b7"
        const val TRACE_PARENT = "00-$TRACE_ID-$SPAN_ID-01"
    }
}

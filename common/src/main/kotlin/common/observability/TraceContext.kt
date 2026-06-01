package common.observability

import org.slf4j.MDC

data class TraceContext(
    val traceId: String? = null,
    val spanId: String? = null,
    val traceParent: String? = null,
) {
    fun isEmpty(): Boolean {
        return traceId.isNullOrBlank() && spanId.isNullOrBlank() && traceParent.isNullOrBlank()
    }

    fun asAttributes(): Map<String, String> {
        return mapOf(
            TRACE_ID_KEY to traceId,
            SPAN_ID_KEY to spanId,
            TRACEPARENT_KEY to traceParent,
        ).filterValues { it != null }.mapValues { checkNotNull(it.value) }
    }

    suspend fun <T> withMdc(block: suspend () -> T): T {
        val previous = mapOf(
            TRACE_ID_KEY to MDC.get(TRACE_ID_KEY),
            TRACE_ID_SNAKE_KEY to MDC.get(TRACE_ID_SNAKE_KEY),
            SPAN_ID_KEY to MDC.get(SPAN_ID_KEY),
            SPAN_ID_SNAKE_KEY to MDC.get(SPAN_ID_SNAKE_KEY),
            TRACEPARENT_KEY to MDC.get(TRACEPARENT_KEY),
            TRACE_PARENT_KEY to MDC.get(TRACE_PARENT_KEY),
        )
        putOrRemove(TRACE_ID_KEY, traceId)
        putOrRemove(TRACE_ID_SNAKE_KEY, traceId)
        putOrRemove(SPAN_ID_KEY, spanId)
        putOrRemove(SPAN_ID_SNAKE_KEY, spanId)
        putOrRemove(TRACEPARENT_KEY, traceParent)
        putOrRemove(TRACE_PARENT_KEY, traceParent)
        return try {
            block()
        } finally {
            previous.forEach { (key, value) -> putOrRemove(key, value) }
        }
    }

    companion object {
        const val TRACEPARENT_KEY = "traceparent"
        const val TRACE_PARENT_KEY = "traceParent"
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_SNAKE_KEY = "trace_id"
        const val SPAN_ID_KEY = "spanId"
        const val SPAN_ID_SNAKE_KEY = "span_id"

        fun current(): TraceContext {
            val traceId = firstMdcValue(TRACE_ID_KEY, TRACE_ID_SNAKE_KEY)
            val spanId = firstMdcValue(SPAN_ID_KEY, SPAN_ID_SNAKE_KEY)
            val traceParent = firstMdcValue(TRACEPARENT_KEY, TRACE_PARENT_KEY)
                ?: buildTraceParent(traceId, spanId)
            return TraceContext(
                traceId = traceId,
                spanId = spanId,
                traceParent = traceParent,
            )
        }

        fun fromHeaders(
            traceParent: String?,
            traceId: String?,
            spanId: String?,
        ): TraceContext {
            val normalizedTraceId = traceId.configuredOrNull()
            val normalizedSpanId = spanId.configuredOrNull()
            val normalizedTraceParent = traceParent.configuredOrNull()
                ?: buildTraceParent(normalizedTraceId, normalizedSpanId)
            return TraceContext(
                traceId = normalizedTraceId,
                spanId = normalizedSpanId,
                traceParent = normalizedTraceParent,
            )
        }

        private fun firstMdcValue(vararg keys: String): String? {
            return keys.firstNotNullOfOrNull { key ->
                MDC.get(key).configuredOrNull()
            }
        }

        private fun buildTraceParent(traceId: String?, spanId: String?): String? {
            if (traceId == null || spanId == null) return null
            val normalizedTraceId = traceId.lowercase()
            val normalizedSpanId = spanId.lowercase()
            if (!normalizedTraceId.matches(TRACE_ID_PATTERN) || !normalizedSpanId.matches(SPAN_ID_PATTERN)) {
                return null
            }
            return "00-$normalizedTraceId-$normalizedSpanId-01"
        }

        private fun String?.configuredOrNull(): String? {
            return this?.trim()?.takeIf(String::isNotBlank)
        }

        private fun putOrRemove(key: String, value: String?) {
            if (value.isNullOrBlank()) {
                MDC.remove(key)
            } else {
                MDC.put(key, value)
            }
        }

        private val TRACE_ID_PATTERN = Regex("[0-9a-f]{32}")
        private val SPAN_ID_PATTERN = Regex("[0-9a-f]{16}")
    }
}

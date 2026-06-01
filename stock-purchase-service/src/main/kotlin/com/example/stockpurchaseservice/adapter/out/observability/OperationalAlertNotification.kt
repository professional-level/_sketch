package com.example.stockpurchaseservice.adapter.out.observability

import org.slf4j.MDC
import java.time.ZonedDateTime

internal data class OperationalAlertNotification(
    val type: String,
    val severity: String,
    val title: String,
    val occurredAt: ZonedDateTime,
    val attributes: Map<String, String?> = emptyMap(),
    val traceContext: OperationalAlertTraceContext = OperationalAlertTraceContext.current(),
)

internal interface OperationalAlertNotificationSink {
    suspend fun send(notification: OperationalAlertNotification)
}

internal data class OperationalAlertTraceContext(
    val traceId: String? = null,
    val spanId: String? = null,
    val traceParent: String? = null,
) {
    fun asAttributes(): Map<String, String> {
        return mapOf(
            "traceId" to traceId,
            "spanId" to spanId,
            "traceparent" to traceParent,
        ).filterValues { it != null }.mapValues { checkNotNull(it.value) }
    }

    companion object {
        fun current(): OperationalAlertTraceContext {
            val traceId = firstMdcValue("traceId", "trace_id")
            val spanId = firstMdcValue("spanId", "span_id")
            val traceParent = firstMdcValue("traceparent", "traceParent")
                ?: buildTraceParent(traceId, spanId)
            return OperationalAlertTraceContext(
                traceId = traceId,
                spanId = spanId,
                traceParent = traceParent,
            )
        }

        private fun firstMdcValue(vararg keys: String): String? {
            return keys.firstNotNullOfOrNull { key ->
                MDC.get(key)?.trim()?.takeIf(String::isNotBlank)
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

        private val TRACE_ID_PATTERN = Regex("[0-9a-f]{32}")
        private val SPAN_ID_PATTERN = Regex("[0-9a-f]{16}")
    }
}

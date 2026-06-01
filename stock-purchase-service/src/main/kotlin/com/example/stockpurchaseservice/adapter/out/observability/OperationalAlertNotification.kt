package com.example.stockpurchaseservice.adapter.out.observability

import common.observability.TraceContext
import java.time.ZonedDateTime

internal data class OperationalAlertNotification(
    val type: String,
    val severity: String,
    val title: String,
    val occurredAt: ZonedDateTime,
    val attributes: Map<String, String?> = emptyMap(),
    val traceContext: TraceContext = TraceContext.current(),
)

internal interface OperationalAlertNotificationSink {
    suspend fun send(notification: OperationalAlertNotification)
}

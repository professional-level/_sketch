package com.example.stockpurchaseservice.adapter.out.observability

import java.time.ZonedDateTime

internal data class OperationalAlertNotification(
    val type: String,
    val severity: String,
    val title: String,
    val occurredAt: ZonedDateTime,
    val attributes: Map<String, String?> = emptyMap(),
)

internal interface OperationalAlertNotificationSink {
    suspend fun send(notification: OperationalAlertNotification)
}

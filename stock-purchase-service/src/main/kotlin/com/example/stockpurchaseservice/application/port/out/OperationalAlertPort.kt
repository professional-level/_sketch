package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import java.time.ZonedDateTime
import java.util.UUID

interface OperationalAlertPort {
    suspend fun alertOrderSubmissionFailed(alert: OrderSubmissionFailureAlert)
    suspend fun alertSubmissionUnknown(alert: SubmissionUnknownAlert)
    suspend fun alertReconciliationFailed(alert: ReconciliationFailureAlert)
}

data class OrderSubmissionFailureAlert(
    val orderIntentId: UUID,
    val idempotencyKey: String,
    val strategyExecutionId: String,
    val symbol: String,
    val side: OrderIntentSide,
    val orderType: OrderIntentType,
    val orderTag: String,
    val reason: String,
    val occurredAt: ZonedDateTime,
)

data class SubmissionUnknownAlert(
    val orderIntentId: UUID,
    val idempotencyKey: String,
    val strategyExecutionId: String,
    val symbol: String,
    val side: OrderIntentSide,
    val orderType: OrderIntentType,
    val orderTag: String,
    val externalOrderId: String?,
    val reason: String?,
    val submittedAt: ZonedDateTime,
    val checkedAt: ZonedDateTime,
)

data class ReconciliationFailureAlert(
    val source: String,
    val reason: String?,
    val failedAt: ZonedDateTime,
)

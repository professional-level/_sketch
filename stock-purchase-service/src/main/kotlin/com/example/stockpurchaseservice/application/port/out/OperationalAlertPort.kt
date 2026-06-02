package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import java.time.ZonedDateTime
import java.util.UUID

interface OperationalAlertPort {
    suspend fun alertOrderSubmissionFailed(alert: OrderSubmissionFailureAlert)
    suspend fun alertSubmissionUnknown(alert: SubmissionUnknownAlert)
    suspend fun alertReconciliationFailed(alert: ReconciliationFailureAlert)
    suspend fun alertUnmatchedExecution(alert: UnmatchedExecutionAlert) = Unit
    suspend fun alertOrderCancellationSubmissionFailed(alert: OrderCancellationSubmissionAlert) = Unit
    suspend fun alertOrderCancellationSubmissionUnknown(alert: OrderCancellationSubmissionAlert) = Unit
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
    val ageSeconds: Long? = null,
    val persistent: Boolean = false,
)

data class ReconciliationFailureAlert(
    val source: String,
    val reason: String?,
    val failedAt: ZonedDateTime,
)

data class UnmatchedExecutionAlert(
    val source: String,
    val externalExecutionId: String,
    val externalOrderId: String,
    val stockId: String,
    val quantity: Int,
    val type: ExecutionTypeDto,
    val reason: String,
    val observedAt: ZonedDateTime,
)

data class OrderCancellationSubmissionAlert(
    val cancellationRequestId: UUID,
    val idempotencyKey: String,
    val strategyExecutionId: String,
    val symbol: String,
    val originalBrokerOrderId: String,
    val branchOrderNumber: String?,
    val reason: String?,
    val occurredAt: ZonedDateTime,
)

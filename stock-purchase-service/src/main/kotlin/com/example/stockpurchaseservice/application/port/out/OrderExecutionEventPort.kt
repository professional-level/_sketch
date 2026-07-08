package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import java.time.ZonedDateTime
import java.util.UUID

interface OrderExecutionEventPort {
    suspend fun publishSubmitted(event: OrderSubmittedMessage)
    suspend fun publishRejected(event: OrderRejectedMessage)
    suspend fun publishCancelled(event: OrderCancelledMessage)
    suspend fun publishFilled(event: OrderFilledMessage)
    suspend fun publishPartiallyFilled(event: OrderPartiallyFilledMessage)
}

data class OrderSubmittedMessage(
    val eventId: UUID,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String,
    val side: OrderIntentSide,
    val orderTag: String,
    val quantity: Long,
    val submittedAt: ZonedDateTime,
    val idempotencyKey: String = eventId.toString(),
)

data class OrderRejectedMessage(
    val eventId: UUID,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String?,
    val reason: String,
    val rejectedAt: ZonedDateTime,
    val idempotencyKey: String = eventId.toString(),
)

data class OrderCancelledMessage(
    val eventId: UUID,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String,
    val reason: String,
    val cancelledAt: ZonedDateTime,
    val idempotencyKey: String = eventId.toString(),
)

data class OrderFilledMessage(
    val eventId: UUID,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String,
    val side: OrderIntentSide,
    val filledPrice: Double,
    val filledQuantity: Long,
    val orderTag: String,
    val filledAt: ZonedDateTime,
    val idempotencyKey: String = eventId.toString(),
)

data class OrderPartiallyFilledMessage(
    val eventId: UUID,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String,
    val side: OrderIntentSide,
    val filledPrice: Double,
    val filledQuantity: Long,
    val orderTag: String,
    val filledAt: ZonedDateTime,
    val idempotencyKey: String = eventId.toString(),
)

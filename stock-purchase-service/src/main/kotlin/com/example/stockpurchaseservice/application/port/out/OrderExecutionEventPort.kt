package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import java.time.ZonedDateTime
import java.util.UUID

interface OrderExecutionEventPort {
    suspend fun publishSubmitted(event: OrderSubmittedMessage)
    suspend fun publishRejected(event: OrderRejectedMessage)
    suspend fun publishFilled(event: OrderFilledMessage)
}

data class OrderSubmittedMessage(
    val eventId: UUID,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String,
    val submittedAt: ZonedDateTime,
)

data class OrderRejectedMessage(
    val eventId: UUID,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String?,
    val reason: String,
    val rejectedAt: ZonedDateTime,
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
)

package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import java.time.ZonedDateTime
import java.util.UUID

interface OrderIntentSubmissionPort {
    suspend fun saveSubmitted(submission: OrderIntentSubmissionDto)
    suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionDto?
}

data class OrderIntentSubmissionDto(
    val orderIntentId: UUID,
    val idempotencyKey: String,
    val strategyExecutionId: String,
    val symbol: String,
    val side: OrderIntentSide,
    val orderType: OrderIntentType,
    val submittedPrice: Double?,
    val quantity: Long,
    val orderTag: String,
    val internalOrderId: UUID,
    val externalOrderId: String,
    val submittedAt: ZonedDateTime,
)

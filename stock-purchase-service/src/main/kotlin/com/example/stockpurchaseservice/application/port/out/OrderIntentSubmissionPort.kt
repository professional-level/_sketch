package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.DEFAULT_OVERSEAS_ORDER_EXCHANGE
import java.time.ZonedDateTime
import java.util.UUID

interface OrderIntentSubmissionPort {
    suspend fun saveSubmitted(submission: OrderIntentSubmissionDto)
    suspend fun saveUnknown(submission: OrderIntentSubmissionDto)
    suspend fun saveRejected(submission: OrderIntentSubmissionDto)
    suspend fun saveCancelled(submission: OrderIntentSubmissionDto)
    suspend fun saveCancelPending(submission: OrderIntentSubmissionDto)
    suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionDto?
    suspend fun findUnknownSubmissions(): List<OrderIntentSubmissionDto>
    suspend fun findCancelPendingSubmissions(): List<OrderIntentSubmissionDto>
}

data class OrderIntentSubmissionDto(
    val orderIntentId: UUID,
    val idempotencyKey: String,
    val strategyExecutionId: String,
    val symbol: String,
    val exchange: String = DEFAULT_OVERSEAS_ORDER_EXCHANGE,
    val market: StockOrderMarket? = null,
    val side: OrderIntentSide,
    val orderType: OrderIntentType,
    val submittedPrice: Double?,
    val quantity: Long,
    val orderTag: String,
    val internalOrderId: UUID,
    val externalOrderId: String?,
    val branchOrderNumber: String? = null,
    val submittedAt: ZonedDateTime,
    val tradingEnvironment: OrderTradingEnvironment? = null,
    val status: OrderIntentSubmissionStatusDto = OrderIntentSubmissionStatusDto.SUBMITTED,
    val statusReason: String? = null,
    val lastStatusCheckedAt: ZonedDateTime? = null,
)

enum class OrderIntentSubmissionStatusDto {
    SUBMITTED,
    SUBMISSION_UNKNOWN,
    REJECTED,
    CANCELLED,
    CANCEL_PENDING,
}

package com.example.stockpurchaseservice.application.port.`in`

import com.example.common.UseCase
import com.example.stockpurchaseservice.application.port.out.OrderTradingEnvironment
import java.time.ZonedDateTime
import java.util.UUID

@UseCase
interface SubmitOrderIntentUseCase {
    suspend fun execute(command: SubmitOrderIntentCommand): SubmitOrderIntentResult
}

data class SubmitOrderIntentCommand(
    val eventId: UUID,
    val idempotencyKey: String,
    val strategyExecutionId: String,
    val symbol: String,
    val side: OrderIntentSide,
    val orderType: OrderIntentType,
    val price: Double?,
    val quantity: Long,
    val orderTag: String,
    val createdAt: ZonedDateTime,
    val tradingEnvironment: OrderTradingEnvironment? = null,
    val exchange: String = DEFAULT_OVERSEAS_ORDER_EXCHANGE,
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(strategyExecutionId.isNotBlank()) { "strategyExecutionId must not be blank" }
        require(symbol.isNotBlank()) { "symbol must not be blank" }
        require(quantity > 0) { "quantity must be positive" }
        require(quantity <= Int.MAX_VALUE) { "quantity must fit Int" }
        require(orderTag.isNotBlank()) { "orderTag must not be blank" }
        if (orderType == OrderIntentType.MOC) {
            require(price == null || price >= 0.0) { "$side $orderType price must not be negative" }
        } else {
            require(price != null && price > 0.0) { "$side $orderType price must be positive" }
        }
    }
}

data class SubmitOrderIntentResult(
    val status: OrderIntentSubmissionStatus,
    val externalOrderId: String? = null,
    val branchOrderNumber: String? = null,
)

enum class OrderIntentSubmissionStatus {
    SUBMITTED,
    SUBMISSION_UNKNOWN,
    SKIPPED_DUPLICATE,
    REJECTED,
}

enum class OrderIntentSide {
    BUY,
    SELL,
}

enum class OrderIntentType {
    LOC,
    MOC,
    LIMIT,
}

const val DEFAULT_OVERSEAS_ORDER_EXCHANGE = "NASD"

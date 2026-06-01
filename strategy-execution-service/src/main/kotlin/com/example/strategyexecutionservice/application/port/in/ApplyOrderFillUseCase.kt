package com.example.strategyexecutionservice.application.port.`in`

import com.example.common.UseCase
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import java.time.ZonedDateTime

@UseCase
interface ApplyOrderFillUseCase {
    suspend fun execute(command: ApplyOrderFillCommand): ApplyOrderFillResult
}

data class ApplyOrderFillCommand(
    val eventId: String,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String,
    val side: OrderSide,
    val fillKind: OrderFillKind = OrderFillKind.FILLED,
    val filledPrice: Double,
    val filledQuantity: Long,
    val orderTag: String,
    val filledAt: ZonedDateTime,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(strategyExecutionId.isNotBlank()) { "strategyExecutionId must not be blank" }
        require(orderIntentId.isNotBlank()) { "orderIntentId must not be blank" }
        require(brokerOrderId.isNotBlank()) { "brokerOrderId must not be blank" }
        require(filledPrice > 0.0) { "filledPrice must be positive" }
        require(filledQuantity > 0) { "filledQuantity must be positive" }
        require(orderTag.isNotBlank()) { "orderTag must not be blank" }
    }
}

enum class OrderFillKind {
    FILLED,
    PARTIALLY_FILLED,
}

data class ApplyOrderFillResult(
    val strategyExecutionId: String,
    val status: ApplyOrderFillStatus,
)

enum class ApplyOrderFillStatus {
    APPLIED,
    SKIPPED_DUPLICATE,
    STRATEGY_NOT_FOUND,
    IGNORED_COMPLETED,
}

package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import java.time.ZonedDateTime
import java.util.UUID

interface OrderRiskControlPort {
    suspend fun assess(command: OrderRiskAssessmentCommand): OrderRiskAssessmentResult
}

data class OrderRiskAssessmentCommand(
    val orderIntentId: UUID,
    val internalOrderId: UUID,
    val idempotencyKey: String,
    val strategyExecutionId: String,
    val symbol: String,
    val side: OrderIntentSide,
    val orderType: OrderIntentType,
    val quantity: Long,
    val limitPrice: Double?,
    val estimatedNotional: Double?,
    val market: StockOrderMarket,
    val orderTag: String,
    val createdAt: ZonedDateTime,
)

data class OrderRiskAssessmentResult(
    val accepted: Boolean,
    val reason: String? = null,
) {
    companion object {
        fun accepted(): OrderRiskAssessmentResult = OrderRiskAssessmentResult(accepted = true)

        fun rejected(reason: String): OrderRiskAssessmentResult {
            return OrderRiskAssessmentResult(accepted = false, reason = reason)
        }
    }
}

package com.example.stockpurchaseservice.adapter.out.broker

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.ExecutionQuantityModeDto
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

internal interface BrokerGateway {
    fun submitOrder(command: BrokerOrderCommand): BrokerOrderSubmissionDto
    fun cancelOrder(command: BrokerOrderCancelCommand): BrokerOrderSubmissionDto
    fun findOrderHistory(query: BrokerOrderHistoryQuery): List<BrokerOrderHistoryItem>
}

internal data class BrokerOrderCommand(
    val internalOrderId: UUID,
    val market: StockOrderMarket,
    val side: OrderIntentSide,
    val symbol: String,
    val orderType: StockOrderType,
    val price: Double,
    val quantity: Int,
    val isMock: Boolean,
)

internal data class BrokerOrderCancelCommand(
    val internalOrderId: UUID,
    val market: StockOrderMarket,
    val symbol: String,
    val originalOrderId: String,
    val branchOrderNumber: String?,
    val orderType: StockOrderType,
    val price: Double,
    val quantity: Int,
    val cancelAll: Boolean,
    val isMock: Boolean,
) {
    init {
        require(symbol.isNotBlank()) { "symbol must not be blank" }
        require(originalOrderId.isNotBlank()) { "originalOrderId must not be blank" }
        require(quantity > 0) { "cancel quantity must be positive" }
    }
}

internal data class BrokerOrderHistoryQuery(
    val market: StockOrderMarket,
    val symbol: String = "",
    val externalOrderId: String = "",
    val from: ZonedDateTime = ZonedDateTime.now(BROKER_ORDER_ZONE),
    val to: ZonedDateTime = from,
    val isMock: Boolean,
    val pageCursor: BrokerOrderHistoryPageCursor = BrokerOrderHistoryPageCursor.EMPTY,
) {
    init {
        require(!from.isAfter(to)) { "broker history from must be before or equal to to" }
    }
}

internal data class BrokerOrderHistoryPage(
    val items: List<BrokerOrderHistoryItem>,
    val nextCursor: BrokerOrderHistoryPageCursor,
)

internal data class BrokerOrderHistoryPageCursor(
    val foreignKeyContext: String = "",
    val nextKeyContext: String = "",
) {
    fun hasNext(): Boolean {
        return foreignKeyContext.isNotBlank() || nextKeyContext.isNotBlank()
    }

    companion object {
        val EMPTY = BrokerOrderHistoryPageCursor()
    }
}

internal data class BrokerOrderHistoryItem(
    val externalOrderId: String,
    val branchOrderNumber: String?,
    val symbol: String,
    val stockName: String,
    val orderedAt: ZonedDateTime,
    val orderedQuantity: Long,
    val cumulativeFilledQuantity: Long,
    val remainingQuantity: Long,
    val rejectedQuantity: Long,
    val cancelledQuantity: Long,
    val cancelled: Boolean,
    val side: OrderIntentSide?,
    val averageExecutionPrice: Double?,
    val statusMessage: String? = null,
    val rejectionReason: String? = null,
) {
    fun toExecutionDto(): ExecutedStockDto? {
        if (cumulativeFilledQuantity <= 0) return null
        val executionType = when (side) {
            OrderIntentSide.SELL -> ExecutionTypeDto.SELLING
            OrderIntentSide.BUY,
            null -> ExecutionTypeDto.PURCHASE
        }
        return ExecutedStockDto(
            stockId = symbol,
            stockName = stockName.ifBlank { symbol },
            createdAt = orderedAt,
            quantity = cumulativeFilledQuantity.toInt(),
            type = executionType,
            externalOrderId = externalOrderId,
            externalExecutionId = "$externalOrderId:${cumulativeFilledQuantity}:$executionType",
            averageExecutionPrice = averageExecutionPrice,
            quantityMode = ExecutionQuantityModeDto.CUMULATIVE,
        )
    }

    fun toStatus(checkedAt: ZonedDateTime = ZonedDateTime.now()): BrokerOrderStatusDto {
        val status = when {
            !rejectionReason.isNullOrBlank() -> BrokerOrderStatus.REJECTED
            statusMessage?.contains(REJECTED_KOREAN_1) == true ||
                statusMessage?.contains(REJECTED_KOREAN_2) == true ||
                statusMessage.containsStatusToken(REJECTED_ENGLISH) -> BrokerOrderStatus.REJECTED
            rejectedQuantity > 0 && cumulativeFilledQuantity <= 0 -> BrokerOrderStatus.REJECTED
            statusMessage?.contains(CANCELLED_KOREAN) == true ||
                statusMessage.containsStatusToken(CANCELLED_ENGLISH_US) ||
                statusMessage.containsStatusToken(CANCELLED_ENGLISH_UK) -> BrokerOrderStatus.CANCELLED
            cancelled || cancelledQuantity > 0 -> BrokerOrderStatus.CANCELLED
            else -> BrokerOrderStatus.SUBMITTED
        }
        return BrokerOrderStatusDto(
            status = status,
            externalOrderId = externalOrderId,
            reason = statusReason(status),
            checkedAt = checkedAt,
        )
    }

    private fun statusReason(status: BrokerOrderStatus): String? {
        return when (status) {
            BrokerOrderStatus.REJECTED -> rejectionReason ?: statusMessage ?: "broker rejected quantity=$rejectedQuantity"
            BrokerOrderStatus.CANCELLED -> statusMessage ?: "broker cancelled quantity=$cancelledQuantity"
            BrokerOrderStatus.SUBMITTED,
            BrokerOrderStatus.UNKNOWN -> null
        }
    }
}

internal fun List<BrokerOrderHistoryItem>.findStatusFor(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
    val candidates = filter { row ->
        when {
            query.externalOrderId != null -> row.externalOrderId == query.externalOrderId
            else -> row.symbol.equals(query.symbol, ignoreCase = true) &&
                row.side == query.side &&
                (query.submittedAt?.toLocalDate()?.let { row.orderedAt.toLocalDate() == it } ?: true)
        }
    }

    return when (candidates.size) {
        0 -> BrokerOrderStatusDto(
            status = BrokerOrderStatus.UNKNOWN,
            externalOrderId = query.externalOrderId,
            reason = "broker order not found",
        )

        1 -> candidates.single().toStatus()
        else -> BrokerOrderStatusDto(
            status = BrokerOrderStatus.UNKNOWN,
            externalOrderId = query.externalOrderId,
            reason = "ambiguous broker orders: ${candidates.joinToString { it.externalOrderId }}",
        )
    }
}

private fun String?.containsStatusToken(token: String): Boolean {
    return this?.contains(token, ignoreCase = true) == true
}

internal val BROKER_ORDER_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

private const val REJECTED_KOREAN_1 = "\uAC70\uBD80"
private const val REJECTED_KOREAN_2 = "\uAC70\uC808"
private const val REJECTED_ENGLISH = "rejected"
private const val CANCELLED_ENGLISH_US = "canceled"
private const val CANCELLED_ENGLISH_UK = "cancelled"
internal const val CANCELLED_KOREAN = "\uCDE8\uC18C"

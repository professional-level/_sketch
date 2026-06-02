package com.example.stockpurchaseservice.adapter.out.broker

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.DEFAULT_OVERSEAS_ORDER_EXCHANGE
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
import kotlin.math.abs

internal interface BrokerGateway {
    fun submitOrder(command: BrokerOrderCommand): BrokerOrderSubmissionDto
    fun cancelOrder(command: BrokerOrderCancelCommand): BrokerOrderSubmissionDto
    fun findCancelableOrders(query: BrokerOrderCancelableQuery): List<BrokerCancelableOrderItem>
    fun findOrderHistory(query: BrokerOrderHistoryQuery): List<BrokerOrderHistoryItem>
    fun findAccountSnapshot(query: BrokerAccountSnapshotQuery): BrokerAccountSnapshot
}

internal data class BrokerOrderCommand(
    val internalOrderId: UUID,
    val market: StockOrderMarket,
    val side: OrderIntentSide,
    val symbol: String,
    val exchange: String = DEFAULT_OVERSEAS_ORDER_EXCHANGE,
    val orderType: StockOrderType,
    val price: Double,
    val quantity: Int,
    val isMock: Boolean,
)

internal data class BrokerOrderCancelCommand(
    val internalOrderId: UUID,
    val market: StockOrderMarket,
    val symbol: String,
    val exchange: String = DEFAULT_OVERSEAS_ORDER_EXCHANGE,
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
    val exchange: String = DEFAULT_OVERSEAS_ORDER_EXCHANGE,
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

internal data class BrokerOrderCancelableQuery(
    val market: StockOrderMarket,
    val symbol: String = "",
    val exchange: String = DEFAULT_OVERSEAS_ORDER_EXCHANGE,
    val originalOrderId: String = "",
    val branchOrderNumber: String? = null,
    val isMock: Boolean,
    val pageCursor: BrokerOrderHistoryPageCursor = BrokerOrderHistoryPageCursor.EMPTY,
)

internal data class BrokerCancelableOrderItem(
    val branchOrderNumber: String?,
    val orderId: String,
    val originalOrderId: String?,
    val symbol: String,
    val possibleQuantity: Long,
) {
    fun matches(query: BrokerOrderCancelableQuery): Boolean {
        val requestedOrderId = query.originalOrderId.trim()
        val sameOrder = requestedOrderId.isBlank() || orderId == requestedOrderId || originalOrderId == requestedOrderId
        val requestedBranch = query.branchOrderNumber?.trim()
        val sameBranch = requestedBranch.isNullOrBlank() || branchOrderNumber?.trim() == requestedBranch
        val sameSymbol = query.symbol.isBlank() || symbol.isBlank() || symbol.equals(query.symbol, ignoreCase = true)
        return sameOrder && sameBranch && sameSymbol
    }
}

internal data class BrokerOrderHistoryPage(
    val items: List<BrokerOrderHistoryItem>,
    val nextCursor: BrokerOrderHistoryPageCursor,
)

internal data class BrokerAccountSnapshotQuery(
    val market: StockOrderMarket,
    val exchange: String = DEFAULT_OVERSEAS_ORDER_EXCHANGE,
    val currency: String = "USD",
    val isMock: Boolean,
    val pageCursor: BrokerOrderHistoryPageCursor = BrokerOrderHistoryPageCursor.EMPTY,
)

internal data class BrokerAccountSnapshot(
    val market: StockOrderMarket,
    val exchange: String,
    val currency: String,
    val positions: List<BrokerPositionSnapshot>,
    val availableCashAmount: Double? = null,
    val cashCurrency: String? = null,
    val orderableCashAmount: Double? = null,
    val settledCashAmount: Double? = null,
    val withdrawableCashAmount: Double? = null,
    val totalPurchaseAmount: Double? = null,
    val totalEvaluationAmount: Double? = null,
    val totalProfitLossAmount: Double? = null,
)

internal data class BrokerPositionSnapshot(
    val symbol: String,
    val stockName: String,
    val quantity: Long,
    val averagePurchasePrice: Double? = null,
    val currentPrice: Double? = null,
    val purchaseAmount: Double? = null,
    val evaluationAmount: Double? = null,
    val profitLossAmount: Double? = null,
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
    val externalExecutionId: String? = null,
    val originalOrderId: String? = null,
    val branchOrderNumber: String?,
    val symbol: String,
    val stockName: String,
    val orderedAt: ZonedDateTime,
    val orderedQuantity: Long,
    val orderedPrice: Double? = null,
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
            externalExecutionId = checkNotNull(effectiveExternalExecutionId(executionType)),
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
            cumulativeFilledQuantity > 0 && isFullyFilled() -> BrokerOrderStatus.FILLED
            cumulativeFilledQuantity > 0 -> BrokerOrderStatus.PARTIALLY_FILLED
            else -> BrokerOrderStatus.SUBMITTED
        }
        return BrokerOrderStatusDto(
            status = status,
            externalOrderId = externalOrderId,
            externalExecutionId = effectiveExternalExecutionId(),
            reason = statusReason(status),
            checkedAt = checkedAt,
            orderedQuantity = orderedQuantity,
            orderedPrice = orderedPrice,
            cumulativeFilledQuantity = cumulativeFilledQuantity,
            remainingQuantity = remainingQuantity,
            averageExecutionPrice = averageExecutionPrice,
            brokerReportedAt = orderedAt,
        )
    }

    private fun isFullyFilled(): Boolean {
        return when {
            orderedQuantity > 0 -> cumulativeFilledQuantity >= orderedQuantity
            else -> remainingQuantity <= 0
        }
    }

    private fun statusReason(status: BrokerOrderStatus): String? {
        return when (status) {
            BrokerOrderStatus.REJECTED -> rejectionReason ?: statusMessage ?: "broker rejected quantity=$rejectedQuantity"
            BrokerOrderStatus.CANCELLED -> statusMessage ?: "broker cancelled quantity=$cancelledQuantity"
            BrokerOrderStatus.PARTIALLY_FILLED -> "broker partially filled quantity=$cumulativeFilledQuantity remaining=$remainingQuantity"
            BrokerOrderStatus.FILLED -> "broker filled quantity=$cumulativeFilledQuantity"
            BrokerOrderStatus.SUBMITTED,
            BrokerOrderStatus.UNKNOWN -> null
        }
    }

    private fun effectiveExternalExecutionId(executionType: ExecutionTypeDto? = null): String? {
        if (!externalExecutionId.isNullOrBlank()) return externalExecutionId
        if (cumulativeFilledQuantity <= 0) return null
        val normalizedExecutionType = executionType ?: when (side) {
            OrderIntentSide.SELL -> ExecutionTypeDto.SELLING
            OrderIntentSide.BUY,
            null -> ExecutionTypeDto.PURCHASE
        }
        return "$externalOrderId:${cumulativeFilledQuantity}:$normalizedExecutionType"
    }
}

internal fun List<BrokerOrderHistoryItem>.findStatusFor(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
    val baseCandidates = filter { row ->
        when {
            query.externalOrderId != null -> row.matchesExternalOrderId(query.externalOrderId)
            else -> row.symbol.equals(query.symbol, ignoreCase = true) &&
                row.side == query.side &&
                (query.submittedAt?.toLocalDate()?.let { row.orderedAt.toLocalDate() == it } ?: true)
        }
    }
    val candidates = baseCandidates
        .narrowByBranchOrderNumber(query)
        .narrowByOrderedQuantity(query)
        .narrowBySubmittedPrice(query)
    val selectedCandidate = candidates.selectStatusCandidate(query)

    return when {
        candidates.isEmpty() -> BrokerOrderStatusDto(
            status = BrokerOrderStatus.UNKNOWN,
            externalOrderId = query.externalOrderId,
            reason = "broker order not found",
        )

        selectedCandidate != null -> selectedCandidate
            .toStatus()
            .withBestFillFrom(candidates, selectedCandidate)
            .normalizeExternalOrderId(query.externalOrderId)
        else -> BrokerOrderStatusDto(
            status = BrokerOrderStatus.UNKNOWN,
            externalOrderId = query.externalOrderId,
            reason = "ambiguous broker orders: ${candidates.joinToString { it.externalOrderId }}",
        )
    }
}

private fun List<BrokerOrderHistoryItem>.selectStatusCandidate(
    query: BrokerOrderStatusQuery,
): BrokerOrderHistoryItem? {
    val externalOrderId = query.externalOrderId ?: return selectSingleTerminalOrSingle()
    return selectCandidateForExternalOrderId(externalOrderId)
}

private fun List<BrokerOrderHistoryItem>.selectCandidateForExternalOrderId(
    externalOrderId: String,
): BrokerOrderHistoryItem? {
    val filledCandidate = filter { it.toStatus().status == BrokerOrderStatus.FILLED }
        .maxWithOrNull(compareBy<BrokerOrderHistoryItem> { it.cumulativeFilledQuantity }.thenBy { it.orderedAt })
    if (filledCandidate != null) return filledCandidate

    val cancelledCandidate = filter { it.toStatus().status == BrokerOrderStatus.CANCELLED }
        .maxWithOrNull(compareBy<BrokerOrderHistoryItem> { it.cancelledQuantity }.thenBy { it.orderedAt })
    if (cancelledCandidate != null) return cancelledCandidate

    val exactSelected = filter { it.externalOrderId == externalOrderId }.selectSingleTerminalOrSingle()
    if (exactSelected != null) return exactSelected

    return filter { row ->
        row.externalOrderId != externalOrderId &&
            row.originalOrderId == externalOrderId &&
            row.toStatus().status != BrokerOrderStatus.REJECTED
    }.selectSingleTerminalOrSingle()
}

private fun List<BrokerOrderHistoryItem>.selectSingleTerminalOrSingle(): BrokerOrderHistoryItem? {
    val terminalCandidates = filter { it.toStatus().status.isTerminalStatus() }
    return when {
        terminalCandidates.size == 1 -> terminalCandidates.single()
        terminalCandidates.size > 1 -> null
        else -> singleOrNull()
    }
}

private fun BrokerOrderStatus.isTerminalStatus(): Boolean {
    return when (this) {
        BrokerOrderStatus.REJECTED,
        BrokerOrderStatus.CANCELLED,
        BrokerOrderStatus.FILLED -> true
        BrokerOrderStatus.SUBMITTED,
        BrokerOrderStatus.PARTIALLY_FILLED,
        BrokerOrderStatus.UNKNOWN -> false
    }
}

private fun List<BrokerOrderHistoryItem>.narrowByBranchOrderNumber(
    query: BrokerOrderStatusQuery,
): List<BrokerOrderHistoryItem> {
    val branchOrderNumber = query.branchOrderNumber?.trim()?.takeIf { it.isNotBlank() } ?: return this
    return filter { row ->
        val rowBranchOrderNumber = row.branchOrderNumber?.trim()?.takeIf { it.isNotBlank() }
        rowBranchOrderNumber == null || rowBranchOrderNumber == branchOrderNumber
    }
}

private fun List<BrokerOrderHistoryItem>.narrowByOrderedQuantity(
    query: BrokerOrderStatusQuery,
): List<BrokerOrderHistoryItem> {
    if (query.externalOrderId != null) return this
    val orderedQuantity = query.orderedQuantity ?: return this
    return filter { it.orderedQuantity == orderedQuantity }
        .takeIf { it.isNotEmpty() }
        ?: this
}

private fun List<BrokerOrderHistoryItem>.narrowBySubmittedPrice(
    query: BrokerOrderStatusQuery,
): List<BrokerOrderHistoryItem> {
    if (query.externalOrderId != null) return this
    val submittedPrice = query.submittedPrice?.takeIf { it > 0.0 } ?: return this
    return filter { row ->
        row.orderedPrice
            ?.takeIf { it > 0.0 }
            ?.matchesSubmittedPrice(submittedPrice) == true
    }
        .takeIf { it.isNotEmpty() }
        ?: this
}

private fun Double.matchesSubmittedPrice(submittedPrice: Double): Boolean {
    return abs(this - submittedPrice) <= PRICE_MATCH_TOLERANCE
}

private fun BrokerOrderHistoryItem.matchesExternalOrderId(externalOrderId: String): Boolean {
    return this.externalOrderId == externalOrderId || originalOrderId == externalOrderId
}

private fun BrokerOrderStatusDto.normalizeExternalOrderId(queriedExternalOrderId: String?): BrokerOrderStatusDto {
    return when {
        queriedExternalOrderId.isNullOrBlank() -> this
        externalOrderId == queriedExternalOrderId -> this
        else -> copy(externalOrderId = queriedExternalOrderId)
    }
}

private fun BrokerOrderStatusDto.withBestFillFrom(
    candidates: List<BrokerOrderHistoryItem>,
    selectedCandidate: BrokerOrderHistoryItem,
): BrokerOrderStatusDto {
    val currentFilledQuantity = cumulativeFilledQuantity ?: 0
    val filledCandidate = candidates
        .filter { it.cumulativeFilledQuantity > currentFilledQuantity }
        .maxWithOrNull(compareBy<BrokerOrderHistoryItem> { it.cumulativeFilledQuantity }.thenBy { it.orderedAt })
        ?: return this
    val filledStatus = filledCandidate.toStatus()
    return copy(
        externalExecutionId = externalExecutionId ?: filledStatus.externalExecutionId,
        orderedQuantity = orderedQuantity ?: filledStatus.orderedQuantity,
        orderedPrice = orderedPrice ?: filledStatus.orderedPrice,
        cumulativeFilledQuantity = filledStatus.cumulativeFilledQuantity,
        averageExecutionPrice = averageExecutionPrice ?: filledStatus.averageExecutionPrice,
        brokerReportedAt = maxOf(
            brokerReportedAt ?: selectedCandidate.orderedAt,
            filledStatus.brokerReportedAt ?: filledCandidate.orderedAt,
        ),
    )
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
private const val PRICE_MATCH_TOLERANCE = 0.000001

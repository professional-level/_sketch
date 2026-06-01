package com.example.stockpurchaseservice.adapter.out.api

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.ExecutionQuantityModeDto
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal data class KisBrokerOrderHistoryRow(
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
                statusMessage?.contains(REJECTED_KOREAN_2) == true -> BrokerOrderStatus.REJECTED
            rejectedQuantity > 0 && cumulativeFilledQuantity <= 0 -> BrokerOrderStatus.REJECTED
            statusMessage?.contains(CANCELLED_KOREAN) == true -> BrokerOrderStatus.CANCELLED
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

internal fun List<KisBrokerOrderHistoryRow>.findStatusFor(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
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

internal fun parseKisOrderDateTime(date: String, time: String): ZonedDateTime {
    val parsedDate = date.takeIf { it.length == 8 }?.let {
        LocalDate.of(
            it.substring(0, 4).toInt(),
            it.substring(4, 6).toInt(),
            it.substring(6, 8).toInt(),
        )
    } ?: LocalDate.now(KIS_ORDER_ZONE)
    val normalizedTime = time.filter(Char::isDigit).padEnd(6, '0').take(6)
    val parsedTime = LocalTime.of(
        normalizedTime.substring(0, 2).toIntOrNull() ?: 0,
        normalizedTime.substring(2, 4).toIntOrNull() ?: 0,
        normalizedTime.substring(4, 6).toIntOrNull() ?: 0,
    )
    return ZonedDateTime.of(parsedDate, parsedTime, KIS_ORDER_ZONE)
}

internal fun String?.toLongValue(): Long {
    return this?.replace(",", "")?.trim()?.toBigDecimalOrNull()?.toLong() ?: 0L
}

internal fun String?.toDoubleValue(): Double? {
    return this?.replace(",", "")?.trim()?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
}

internal fun String?.toOrderIntentSide(): OrderIntentSide? {
    return when (this?.trim()) {
        "01" -> OrderIntentSide.SELL
        "02" -> OrderIntentSide.BUY
        else -> null
    }
}

private val KIS_ORDER_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
private const val REJECTED_KOREAN_1 = "\uAC70\uBD80"
private const val REJECTED_KOREAN_2 = "\uAC70\uC808"
internal const val CANCELLED_KOREAN = "\uCDE8\uC18C"

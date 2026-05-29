package com.example.stockpurchaseservice.application.port.out

import java.time.ZonedDateTime
import java.util.UUID

interface MarketServicePort {
    fun buyStock(order: PurchaseOrderDto)
    fun sellStock(order: SellingOrderDto)
    fun findExecutionListAtOneDay(): List<ExecutedStockDto>
}

data class PurchaseOrderDto(
    val orderId: UUID,
    val stockId: String,
    val purchasePrice: Double,
    val quantity: Int,
)

data class SellingOrderDto(
    val orderId: UUID,
    val stockId: String,
    val sellingPrice: Double,
    val quantity: Int,
)

data class ExecutedStockDto(
    val stockId: String,
    val stockName: String,
    val createdAt: ZonedDateTime,
    val quantity: Int,
    val type: ExecutionTypeDto,
    val externalOrderId: String,
    val externalExecutionId: String,
)

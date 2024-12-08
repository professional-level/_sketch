package com.example.stockpurchaseservice.application.port.out

import com.example.com.example.stockpurchaseservice.domain.ExecutedStock
import java.util.UUID

interface MarketServicePort {
    fun buyStock(order: PurchaseOrderDto)
    fun sellStock(order: SellingOrderDto)
    fun findExecutionListAtOneDay() : List<ExecutedStock>
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

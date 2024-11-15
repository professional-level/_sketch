package com.example.com.example.stockpurchaseservice.application.port.out

import java.util.UUID

interface MarketServicePort {
    fun buyStock(order: PurchaseOrderDto)
}

data class PurchaseOrderDto(
    val orderId: UUID,
    val stockId: String,
    val purchasePrice: Double,
)
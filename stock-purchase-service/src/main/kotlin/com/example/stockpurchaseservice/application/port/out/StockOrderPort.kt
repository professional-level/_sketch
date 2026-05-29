package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.repository.OrderDto
import java.util.UUID

interface StockOrderPort {
    suspend fun findById(id: UUID): OrderDto?
    suspend fun save(order: OrderDto)
    suspend fun existsByStrategyId(strategyId: String): Boolean
    suspend fun findAllWithNotCompleted(): List<OrderDto>
    suspend fun findAllWithPurchaseWaiting(): List<OrderDto>
    suspend fun findByStockIdAndQuantity(stockId: String, quantity: Int): OrderDto?
    suspend fun saveExternalOrderId(internalOrderId: UUID, externalOrderId: String)
    suspend fun findByExternalOrderId(value: String): OrderDto?
}

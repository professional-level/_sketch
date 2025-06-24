package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.repository.OrderDto
import java.util.UUID

interface StockOrderPort {
    suspend fun save(order: OrderDto)
    suspend fun findAllWithNotCompleted(): List<OrderDto>
    suspend fun saveExternalOrderId(internalOrderId: UUID, externalOrderId: String)
    suspend fun findByExternalOrderId(value: String): OrderDto?
}

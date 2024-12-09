package com.example.com.example.stockpurchaseservice.domain.repository

import com.example.common.DomainRepository
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.OrderId
import com.example.stockpurchaseservice.domain.StockId

interface StockOrderRepository : DomainRepository<Order, OrderId> {
    suspend fun save(order: Order)
    suspend fun findAllNotCompleted(): List<Order>
    suspend fun findByStockIdAndQuantity(stockId: StockId, quantity:Int): Order?
}

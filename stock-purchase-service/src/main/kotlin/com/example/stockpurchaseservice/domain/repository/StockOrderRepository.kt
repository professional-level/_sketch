package com.example.com.example.stockpurchaseservice.domain.repository

import com.example.common.DomainRepository
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.OrderId

interface StockOrderRepository : DomainRepository<Order, OrderId> {
    suspend fun save(order: Order)
    suspend fun findAllNotCompleted(): List<Order>
}

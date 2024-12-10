package com.example.com.example.stockpurchaseservice.domain.repository

import com.example.common.DomainRepository
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.OrderId


interface StockOrderRepository: DomainRepository<Order, OrderId>{
    fun save(order: Order)
}
package com.example.com.example.stockpurchaseservice.application.port.out

import com.example.com.example.stockpurchaseservice.application.repository.OrderDto

interface StockOrderPort {
    fun save(order: OrderDto)
}

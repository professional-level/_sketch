package com.example.com.example.stockpurchaseservice.adapter.out

import com.example.com.example.stockpurchaseservice.adapter.out.persistence.entity.Order
import com.example.com.example.stockpurchaseservice.adapter.out.persistence.repository.StockOrderRepository
import com.example.com.example.stockpurchaseservice.application.port.out.StockOrderPort
import com.example.com.example.stockpurchaseservice.application.repository.OrderDto
import com.example.common.PersistenceAdapter

@PersistenceAdapter
internal class StockOrderAdapter(
    private val stockOrderRepository: StockOrderRepository,
) : StockOrderPort {
    override suspend fun save(order: OrderDto) {
        stockOrderRepository.save(Order.from(order))
    }
}
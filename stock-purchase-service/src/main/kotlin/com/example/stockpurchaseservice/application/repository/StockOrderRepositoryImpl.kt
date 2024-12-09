package com.example.com.example.stockpurchaseservice.application.repository

import com.example.com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.OrderId

class StockOrderRepositoryImpl(
    private val repository: StockOrderRepositoryPort, // TODO: 이름 체크
) : StockOrderRepository {
    override fun findById(id: OrderId): Order? {
        TODO("Not yet implemented")
    }

    // TODO: save 구현은 왜 DomainRepo에 없을까? 구현필요
    override fun save(order: Order) {
        repository.save(order.toDto()) // TODO: layer 규칙때문에 dto 반환 필요
    }
}
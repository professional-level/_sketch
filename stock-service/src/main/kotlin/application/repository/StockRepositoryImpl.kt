package com.example.stock.application.repository

import com.example.stock.application.port.out.StockRepositoryPort
import com.example.stock.domain.Stock
import com.example.stock.domain.StockId
import com.example.stock.domain.repository.StockRepository
import org.springframework.stereotype.Repository

@Repository
class StockRepositoryImpl(
    private val repository: StockRepositoryPort,
) : StockRepository {
    override fun findById(id: StockId): Stock? {
        return repository.findById(id.value)?.toStock()
    }
}
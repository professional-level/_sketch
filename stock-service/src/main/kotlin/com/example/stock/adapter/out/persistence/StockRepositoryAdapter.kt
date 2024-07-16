package com.example.stock.adapter.out.persistence


import adapter.out.persistence.repository.StockJpaRepository
import com.example.stock.application.port.out.dto.StockDTO
import com.example.stock.common.PersistenceAdapter
import com.example.stock.stock.application.port.out.StockRepositoryPort

@PersistenceAdapter
class StockRepositoryAdapter(
        private val repository: adapter.out.persistence.repository.StockJpaRepository,
) : StockRepositoryPort {
    override fun findById(id: Long): StockDTO? {
        return repository.findById(id.toInt())?.toDTO()
    }
}
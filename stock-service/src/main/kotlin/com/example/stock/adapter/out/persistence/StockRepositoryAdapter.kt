package com.example.stock.adapter.out.persistence



import com.example.stock.adapter.out.persistence.repository.StockJpaRepository
import com.example.stock.application.port.out.StockRepositoryPort
import com.example.stock.application.port.out.dto.StockDTO
import com.example.stock.common.PersistenceAdapter


@PersistenceAdapter
class StockRepositoryAdapter(
    private val repository: StockJpaRepository,
) : StockRepositoryPort {
    override fun findById(id: Long): StockDTO? {
        return repository.findById(id.toInt())?.toDTO()
    }
}
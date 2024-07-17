package com.example.stock.adapter.out.persistence.repository


import com.example.stock.adapter.out.persistence.entity.StockJpaEntity
import org.springframework.stereotype.Repository

@Repository
class StockJpaRepository {
    fun save(): StockJpaEntity {
        return StockJpaEntity(id = 1, name = "name", price = 400, derivative = 3.7)
    }

    fun findById(id: Int): StockJpaEntity? {
        return StockJpaEntity(id = 2, name = "name2", price = 500, derivative = 4.7)
    }
}
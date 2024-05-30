package com.example.stock.adapter.out.persistence

import org.springframework.stereotype.Repository

@Repository
class StockJpaRepository {
    fun save(): StockJpaEntity {
        return StockJpaEntity(id = 1, name = "name", price = 400, derivative = 3.7)
    }

}
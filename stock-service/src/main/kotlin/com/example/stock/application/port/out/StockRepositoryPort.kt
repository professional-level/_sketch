package com.example.stock.application.port.out

import com.example.stock.application.port.out.dto.StockDTO

interface StockRepositoryPort {
    fun findById(id: Long): StockDTO?
}
package com.example.stocksearchservice.application.port.out

import com.example.stocksearchservice.application.port.out.dto.StockStrategyDTO

interface StockStrategyPort {
    suspend fun save(dto: StockStrategyDTO)
}
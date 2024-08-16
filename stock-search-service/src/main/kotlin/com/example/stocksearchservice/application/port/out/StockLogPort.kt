package com.example.stocksearchservice.application.port.out

import com.example.stocksearchservice.application.port.out.dto.StockVolumeRankDTO

interface StockLogPort {
    suspend fun saveStockVolumeRankInfo(items: List<StockVolumeRankDTO>)
}
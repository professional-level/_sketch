package com.example.stock.application.port.out

import com.example.stock.application.port.out.dto.StockDTO

interface AssembleStockInfoPort {

    fun assembleStockInfo(
        stockId: Int,
        stockName: String,
        stockDerivative: Double,
        stockPrice: Int,
    ): StockDTO
}
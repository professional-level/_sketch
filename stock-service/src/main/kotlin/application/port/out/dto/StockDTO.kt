package com.example.stock.application.port.out.dto

import com.example.stock.domain.Stock

data class StockDTO(
    val stockId: Long,
    val stockName: String,
    val stockPrice: Int,
    val stockDerivative: Double,
) {
    fun toStock(): Stock {
        return Stock.default()
    }

    companion object {
        fun fromStock(stock: Stock): StockDTO {
            return StockDTO(
                stockId = stock.stockId.value,
                stockName = stock.stockName.value,
                stockPrice = stock.stockPrice.value,
                stockDerivative = stock.stockDerivative.value,
            )
        }
    }
}
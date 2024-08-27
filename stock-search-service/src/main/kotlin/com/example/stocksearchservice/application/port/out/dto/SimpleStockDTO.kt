package com.example.stocksearchservice.application.port.out.dto

import com.example.stocksearchservice.domain.Stock
import com.example.stocksearchservice.domain.StockDerivative
import com.example.stocksearchservice.domain.StockId
import com.example.stocksearchservice.domain.StockName
import com.example.stocksearchservice.domain.StockPrice
import com.example.stocksearchservice.domain.StockVolume

data class SimpleStockDTO(
    val stockId: Int, // 주식코드
    val stockName: String, // 주식이름
    val stockPrice: Int, // 주식 현재가격
    val stockDerivative: Double, // 주식 증감율
    val stockVolume: Long, // 주식 거래 대금
    val date: String, // 해당 날짜
) {
    fun toStock(): Stock {
        return Stock.of(
            stockId = StockId.of(stockId),
            stockName = StockName.of(stockName),
            stockPrice = StockPrice.of(stockPrice),
            stockDerivative = StockDerivative.of(stockDerivative),
            stockVolume = StockVolume.of(stockVolume),
        )
    }

    companion object {
        fun fromStock(stock: Stock): SimpleStockDTO {
            return SimpleStockDTO(
                stockId = stock.stockId.value,
                stockName = stock.stockName.value,
                stockPrice = stock.stockPrice.value,
                stockDerivative = stock.stockDerivative.value,
                stockVolume = stock.stockVolume.value,
                date = "", // TODO: domain entity에 date가 있어야 할까? 추가에 대한 고민
            )
        }
    }
}

package com.example.stocksearchservice.application.port.out.dto

import com.example.stocksearchservice.domain.Stock
import com.example.stocksearchservice.domain.StockLog
import java.time.ZonedDateTime

data class StockVolumeRankDTO(
    val stockId: Int, // 주식코드
    val stockPrice: Int, // 주식 현재가격
    val stockDerivative: Double, // 주식 증감율
    val stockVolume: Long, // 주식 거래 대금
    val dateTime: ZonedDateTime, // 해당 날짜
    val rank: Int, // 순위
) {
    fun toStock(): Stock {
        return Stock.default()
    }

    companion object {
        fun fromStockLog(log: StockLog): StockVolumeRankDTO {
            return StockVolumeRankDTO(
                stockId = log.stock.stockId.value,
                stockPrice = log.stock.stockPrice.value,
                stockDerivative = log.stock.stockDerivative.value,
                stockVolume = log.stock.stockVolume.value,
                dateTime = requireNotNull(log.stockLogInfo).dateTime,
                rank = requireNotNull(log.stockLogInfo).rank,
            )
        }

        fun fromStock(stock: Stock, dateTime: ZonedDateTime, rank: Int): StockVolumeRankDTO {
            return StockVolumeRankDTO(
                stockId = stock.stockId.value,
                stockPrice = stock.stockPrice.value,
                stockDerivative = stock.stockDerivative.value,
                stockVolume = stock.stockVolume.value,
                dateTime = dateTime,
                rank = rank,
            )
        }
    }
}

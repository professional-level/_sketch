package com.example.stocksearchservice.domain

import java.time.ZonedDateTime

class StockLog private constructor(
    val stock: Stock,
    val stockLogInfo: StockLogInfo?,
) {
    companion object {
        fun from(stockList: List<Stock>): List<StockLog> {
            val time = ZonedDateTime.now()
           return stockList.mapIndexed { index, stock ->
                StockLog(
                    stock = stock,
                    stockLogInfo = StockLogInfo(
                        dateTime = time, rank = index + 1
                    )
                )
            }
        }
    }
}

data class StockLogInfo(
    val dateTime: ZonedDateTime, // TODO: localDate, localTime으로 분리할것인지 localDateTime을 사용할것인지 결정해야함
    val rank: Int,
)

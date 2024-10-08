package com.example.stocksearchservice.application.port.out.dto

import java.time.ZonedDateTime

data class StockStrategyDTO(
    val stockId: String, // 주식코드
    val type: StrategyType, // 알고리즘 타입
    val date: ZonedDateTime, // 해당 날짜
)

enum class StrategyType {
    FinalPriceBatingV1,
}

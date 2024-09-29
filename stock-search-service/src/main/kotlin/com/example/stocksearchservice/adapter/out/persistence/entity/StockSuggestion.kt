package com.example.stocksearchservice.adapter.out.persistence.entity

import com.example.stocksearchservice.application.port.out.dto.StockStrategyDTO
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Table(name = "stock_suggestion")
@Entity
class StockSuggestion(
    @Id
    val sequence: Long? = null,
    @Column(nullable = false)
    val id: String,
//    @Column(nullable = false)
//    val name: String,
//    @Column(nullable = true) // 증감율 정보가 없을 수 있나?
//    val derivative: Double?, // 주식 증감율
//    @Column(nullable = true)
//    val volume: Long?, // 주식 거래 대금
//    @Column(nullable = true)
    val dateTime: ZonedDateTime, // 해당 날짜 // TODO: ZonnedDateTime으로 수정 필요
    @Enumerated(EnumType.STRING)
    @Column
    val strategyType: StrategyType, // 알고리즘 타입
) {
    fun default() {
    }

    companion object {
        fun of(dto: StockStrategyDTO) =
            StockSuggestion(id = dto.stockId, dateTime = dto.date, strategyType = StrategyType.from(dto.type))
    }
}

enum class StrategyType {
    FinalPriceBatingV1, // 종가 베팅 v1
    //    TradeWithFinalPriceStrategy, // 종가 베팅 // TODO:
    Default, // 기본값
    ;
    companion object {
        fun from(type: com.example.stocksearchservice.application.port.out.dto.StrategyType): StrategyType {
            return when (type) {
                com.example.stocksearchservice.application.port.out.dto.StrategyType.FinalPriceBatingV1 -> FinalPriceBatingV1
            }
        }
    }
}

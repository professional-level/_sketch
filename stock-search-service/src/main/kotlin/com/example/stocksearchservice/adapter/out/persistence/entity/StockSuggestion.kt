package com.example.stocksearchservice.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Table(name = "stock_suggestion")
@Entity
class StockSuggestion(
    @Id
    val sequence: Long,
    @Column(nullable = false)
    val id: Int,
    @Column(nullable = false)
    val name: String,
    @Column(nullable = true) // 증감율 정보가 없을 수 있나?
    val derivative: Double?, // 주식 증감율
    @Column(nullable = true)
    val volume: Long?, // 주식 거래 대금
    @Column
    val dateTime: LocalDateTime, // 해당 날짜
    @Enumerated(EnumType.STRING)
    @Column
    val algorithmType: AlgorithmType, // 알고리즘 타입
) {
    fun default() {
    }
}

enum class AlgorithmType {
    TradeWithFinalPriceStrategy, // 종가 베팅
    Default, // 기본값
}

package com.example.stocksearchservice.application.port.out.dto

data class StockProgramVolume(
    val stockId: Int, // 주식코드
    val stockPrice: Int, // 주식 현재가격
    val stockDerivative: Double, // 주식 증감율
    val stockVolume: Long, // 주식 거래 대금
    val programVolume: Long, // 프로그램 거래대금
)

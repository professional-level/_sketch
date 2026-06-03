package com.example.backtestservice.domain.market

import java.math.BigDecimal
import java.time.LocalDate

data class HistoricalCandle(
    val symbol: String,
    val market: String,
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val adjustedClose: BigDecimal,
    val volume: Long,
    val source: String,
)

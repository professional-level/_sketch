package com.example.stockpurchaseservice.domain

import java.time.ZonedDateTime

data class ExecutedStock(
    val stock: Stock,
    val createdAt: ZonedDateTime,
    val quantity: Int,
    val type: ExecutionType,
    val externalOrderId: ExternalOrderId
    // 매수가격, 익절 목표 가격, 손절 목표 가격 명시 필요
)

enum class ExecutionType {
    Selling,
    Purchase
}

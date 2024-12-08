package com.example.com.example.stockpurchaseservice.domain

import com.example.stockpurchaseservice.domain.Stock
import java.time.ZonedDateTime

class ExecutedStock(
    val stock: Stock,
    val createdAt: ZonedDateTime,
    val quantity: Int,
    val type:ExecutionType,
)
enum class ExecutionType{
    Selling,
    Purchase
}
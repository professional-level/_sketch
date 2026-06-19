package com.example.sketch.openapi.toss.domain

enum class TossOrderSide {
    BUY,
    SELL,
}

enum class TossOpenApiOperation {
    ACCOUNT,
    STOCK_SNAPSHOT,
    STOCK_BALANCE,
    ORDER,
    ORDER_SIMULATION,
}

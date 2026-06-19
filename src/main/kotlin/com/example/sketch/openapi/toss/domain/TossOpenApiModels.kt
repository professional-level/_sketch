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

open class TossOpenApiException(message: String) : RuntimeException(message)

class TossOpenApiConfigurationException(message: String) : TossOpenApiException(message)

class TossOpenApiTokenException(message: String) : TossOpenApiException(message)

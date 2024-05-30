package com.example.stock.application.port.`in`

class AssembleStockInfoCommand(
    val stockId: String,
    val stockName: String,
    val stockPrice: Int,
    val stockDerivative: Double,
) {
    init {
        // TODO: stockId validation
        require(stockName.isNotEmpty()) { "The stockName must be 1 characters at least" }
        require(stockPrice > 0) { "The stockPrice must be greater than 0" }
    }
}
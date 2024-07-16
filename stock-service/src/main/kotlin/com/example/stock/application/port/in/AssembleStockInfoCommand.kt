package com.example.stock.application.port.`in`

import com.example.stock.domain.Stock

class AssembleStockInfoCommand(
    val stockId: Int,
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

data class AssembleStockInfoCommandResult(
    val stockId: Int,
    val stockName: String,
    val stockPrice: Int,
    val stockDerivative: Double,
) {
    companion object {
        fun Stock.toCommandResult(): AssembleStockInfoCommandResult {
            return AssembleStockInfoCommandResult(
                    stockId = stockId.value.toInt(),
                    stockName = stockName.value,
                    stockPrice = stockPrice.value,
                    stockDerivative = stockDerivative.value
            )
        }
    }
}
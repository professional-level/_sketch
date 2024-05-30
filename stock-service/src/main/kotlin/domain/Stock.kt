package com.example.stock.domain


class Stock private constructor(
    val stockId: StockId,
    val stockName: StockName,
    val stockPrice: StockPrice,
    val stockDerivative: StockDerivative,
) {
    companion object {
        fun of(
            stockId: StockId,
            stockName: StockName,
            stockPrice: StockPrice,
            stockDerivative: StockDerivative,
        ): Stock {
            return Stock(
                stockId = stockId,
                stockName = stockName,
                stockPrice = stockPrice,
                stockDerivative = stockDerivative,
            )
        }
    }
}


@JvmInline
value class StockId private constructor(val value: Long)

@JvmInline
value class StockName private constructor(val value: String)

@JvmInline
value class StockPrice private constructor(val value: Int)

@JvmInline
value class StockDerivative private constructor(val value: Double)

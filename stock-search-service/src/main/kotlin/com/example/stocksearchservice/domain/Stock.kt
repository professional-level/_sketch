package com.example.stocksearchservice.domain

class Stock private constructor(
    val stockId: StockId,
    val stockName: StockName,
    val stockPrice: StockPrice,
    val stockDerivative: StockDerivative,
    val stockVolume: StockVolume,
) {
    companion object {
        fun of(
            stockId: StockId,
            stockName: StockName,
            stockPrice: StockPrice,
            stockDerivative: StockDerivative,
            stockVolume: StockVolume,
        ): Stock {
            return Stock(
                stockId = stockId,
                stockName = stockName,
                stockPrice = stockPrice,
                stockDerivative = stockDerivative,
                stockVolume = stockVolume,
            )
        }

        fun default(): Stock {
            return Stock(
                stockId = StockId.default(),
                stockName = StockName.default(),
                stockPrice = StockPrice.default(),
                stockDerivative = StockDerivative.default(),
                stockVolume = StockVolume.default(),
            )
        }
    }
}

// TODO: typealias말고 좀 더 고민이 필요 한 듯 하다.
typealias ForeignerStockVolume = StockVolume
typealias InstitutionStockVolume = StockVolume

@JvmInline
value class StockVolume private constructor(val value: Int) {
    companion object {
        fun of(value: Int): StockVolume {
            return StockVolume(value)
        }
        fun default(): StockVolume = StockVolume(value = 0)
    }
}

@JvmInline
value class StockId private constructor(val value: Long) {
    companion object {
        fun default(): StockId = StockId(value = 0)
    }
}

@JvmInline
value class StockName private constructor(val value: String) {
    companion object {
        fun default(): StockName = StockName(value = "name")
    }
}

@JvmInline
value class StockPrice private constructor(val value: Int) {
    companion object {
        fun default(): StockPrice = StockPrice(value = 1000)
    }
}

@JvmInline
value class StockDerivative private constructor(val value: Double) {
    companion object {
        fun default(): StockDerivative = StockDerivative(value = 3.3)
    }
}

package com.example.stocksearchservice.domain

class Stock private constructor(
    val stockId: StockId, // 불변하는 값
    val stockName: StockName, // 불변하는 값
    val stockPrice: StockPrice, // 변하는 값. state를 가져야 하나 ?
    val stockDerivative: StockDerivative, // 변하는 값. state를 가져야 하나 ?
    val stockVolume: StockVolume, // 변하는 값. state를 가져야 하나 ?
    val stockTotalVolume: StockTotalVolume,
) {
    companion object {
        fun of(
            stockId: StockId,
            stockName: StockName,
            stockPrice: StockPrice,
            stockDerivative: StockDerivative,
            stockVolume: StockVolume,
            stockTotalVolume: StockTotalVolume,
        ): Stock {
            return Stock(
                stockId = stockId,
                stockName = stockName,
                stockPrice = stockPrice,
                stockDerivative = stockDerivative,
                stockVolume = stockVolume,
                stockTotalVolume = stockTotalVolume,
            )
        }

        fun default(): Stock {
            return Stock(
                stockId = StockId.default(),
                stockName = StockName.default(),
                stockPrice = StockPrice.default(),
                stockDerivative = StockDerivative.default(),
                stockVolume = StockVolume.default(),
                stockTotalVolume = StockTotalVolume.default(),
            )
        }
    }
}

// TODO: typealias말고 좀 더 고민이 필요 한 듯 하다.
typealias ForeignerStockVolume = StockVolume
typealias InstitutionStockVolume = StockVolume

@JvmInline
value class StockTotalVolume private constructor(val value: Long) {
    companion object {
        fun of(value: Long): StockTotalVolume = StockTotalVolume(value)
        fun default(): StockTotalVolume = StockTotalVolume(value = 0)
    }
}

@JvmInline
value class StockVolume private constructor(val value: Long) {
    companion object {
        fun of(value: Long): StockVolume = StockVolume(value)
        fun default(): StockVolume = StockVolume(value = 0)
    }
}

@JvmInline
value class StockId private constructor(val value: Int) {
    companion object {
        fun default(): StockId = StockId(value = 0)
        fun of(stockId: Int): StockId = StockId(stockId)
    }
}

@JvmInline
value class StockName private constructor(val value: String) {
    companion object {
        fun default(): StockName = StockName(value = "name")
        fun of(stockName: String): StockName = StockName(stockName)
    }
}

@JvmInline
value class StockPrice private constructor(val value: Int) {
    companion object {
        fun default(): StockPrice = StockPrice(value = 1000)
        fun of(value: Int): StockPrice = StockPrice(value = value)
        fun getDiffDerivativeBetween(high: StockPrice, low: StockPrice): StockDerivative {
            val result = (high.value - low.value).toDouble() / high.value.toDouble() // TODO: high.value에 toDobule이 필요한지 check
            return StockDerivative.of(result)
        }
    }
}

@JvmInline
value class StockDerivative private constructor(val value: Double) {
    companion object {
        fun default(): StockDerivative = StockDerivative(value = 3.3)
        fun of(value: Double): StockDerivative = StockDerivative(value)
    }
}

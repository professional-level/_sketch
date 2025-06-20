package com.example.common.domain.stock

import java.time.ZonedDateTime

/**
 * 통합된 Stock 도메인 모델
 * 모든 서비스에서 공통으로 사용할 수 있는 Stock 엔티티
 */
data class Stock private constructor(
    val stockId: StockId,
    val stockName: StockName,
    val stockPrice: StockPrice,
    val stockDerivative: StockDerivative,
    val stockVolume: StockVolume?,
    val stockTotalVolume: StockTotalVolume?,
) {
    companion object {
        /**
         * 전체 정보를 포함한 Stock 생성 (stock-search-service용)
         */
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

        /**
         * 기본 정보만 포함한 Stock 생성 (stock-service용)
         */
        fun basic(
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
                stockVolume = null,
                stockTotalVolume = null,
            )
        }

        /**
         * 간단한 Stock 생성 (stock-purchase-service용)
         */
        fun simple(
            stockId: StockId,
            stockName: StockName,
        ): Stock {
            return Stock(
                stockId = stockId,
                stockName = stockName,
                stockPrice = StockPrice.default(),
                stockDerivative = StockDerivative.default(),
                stockVolume = null,
                stockTotalVolume = null,
            )
        }

        fun default(): Stock {
            return Stock(
                stockId = StockId.default(),
                stockName = StockName.default(),
                stockPrice = StockPrice.default(),
                stockDerivative = StockDerivative.default(),
                stockVolume = null,
                stockTotalVolume = null,
            )
        }
    }

    /**
     * stock-purchase-service에서 사용하는 간단한 형태로 변환
     */
    fun toSimpleStock(): SimpleStock {
        return SimpleStock(
            id = stockId,
            name = stockName.value,
        )
    }
}

/**
 * stock-purchase-service에서 사용하는 간단한 Stock 모델
 */
data class SimpleStock(
    val id: StockId,
    val name: String,
)

// Value Objects
@JvmInline
value class StockId private constructor(val value: String) {
    companion object {
        fun of(value: String): StockId = StockId(value)
        fun of(value: Long): StockId = StockId(value.toString())
        fun default(): StockId = StockId("000000")
    }
}

@JvmInline
value class StockName private constructor(val value: String) {
    companion object {
        fun of(value: String): StockName = StockName(value)
        fun default(): StockName = StockName("UNKNOWN")
    }
}

@JvmInline
value class StockPrice private constructor(val value: Int) {
    companion object {
        fun of(value: Int): StockPrice = StockPrice(value)
        fun default(): StockPrice = StockPrice(1000)
        
        fun getDiffDerivativeBetween(high: StockPrice, low: StockPrice): StockDerivative {
            val result = (high.value - low.value).toDouble() / high.value.toDouble()
            return StockDerivative.of(result)
        }
    }
}

@JvmInline
value class StockDerivative private constructor(val value: Double) {
    companion object {
        fun of(value: Double): StockDerivative = StockDerivative(value)
        fun default(): StockDerivative = StockDerivative(0.0)
    }
}

@JvmInline
value class StockVolume private constructor(val value: Long) {
    companion object {
        fun of(value: Long): StockVolume = StockVolume(value)
        fun default(): StockVolume = StockVolume(0L)
    }
}

@JvmInline
value class StockTotalVolume private constructor(val value: Long) {
    companion object {
        fun of(value: Long): StockTotalVolume = StockTotalVolume(value)
        fun default(): StockTotalVolume = StockTotalVolume(0L)
    }
}

// Type aliases for specific use cases
typealias ForeignerStockVolume = StockVolume
typealias InstitutionStockVolume = StockVolume
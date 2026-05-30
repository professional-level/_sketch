package com.example.strategyexecutionservice.domain.strategy.laor

data class LaorV4StrategyConfig(
    val symbol: LaorV4StrategySymbol,
    val splits: Int,
    val firstBuyMultiplier: Double = DEFAULT_FIRST_BUY_MULTIPLIER,
) {
    init {
        require(splits > 1) { "splits must be greater than 1" }
        require(firstBuyMultiplier > 1.0) { "firstBuyMultiplier must be greater than 1" }
    }

    val reverseSellDivisor: Double
        get() = splits / 2.0

    val reverseSellFactor: Double
        get() = 1.0 - 2.0 / splits

    companion object {
        const val DEFAULT_FIRST_BUY_MULTIPLIER: Double = 1.12
    }
}

data class LaorV4StrategyState(
    val mode: LaorV4StrategyMode = LaorV4StrategyMode.NORMAL,
    val t: Double = 0.0,
    val cash: Double,
    val shares: Long = 0,
    val avgPrice: Double = 0.0,
    val realizedPnl: Double = 0.0,
    val reverseDays: Int = 0,
) {
    init {
        require(t >= 0.0) { "t must not be negative" }
        require(cash >= 0.0) { "cash must not be negative" }
        require(shares >= 0) { "shares must not be negative" }
        require(avgPrice >= 0.0) { "avgPrice must not be negative" }
        require(shares == 0L || avgPrice > 0.0) { "avgPrice must be positive when shares are held" }
        require(reverseDays >= 0) { "reverseDays must not be negative" }
    }
}

data class LaorV4StrategyMarket(
    val previousClose: Double,
    val recentClosePrices: List<Double> = emptyList(),
) {
    init {
        require(previousClose > 0.0) { "previousClose must be positive" }
        require(recentClosePrices.all { it > 0.0 }) { "recentClosePrices must be positive" }
    }
}

data class LaorV4StrategyOrder(
    val side: LaorV4StrategySide,
    val type: LaorV4StrategyOrderType,
    val price: Double?,
    val quantity: Long,
    val tag: LaorV4StrategyOrderTag,
) {
    init {
        require(quantity > 0) { "quantity must be positive" }
        if (type == LaorV4StrategyOrderType.MOC) {
            require(price == null || price > 0.0) { "MOC price must be null or positive" }
        } else {
            require(price != null && price > 0.0) { "$type price must be positive" }
        }
    }
}

data class LaorV4StrategyFill(
    val side: LaorV4StrategySide,
    val price: Double,
    val quantity: Long,
    val tag: LaorV4StrategyOrderTag,
) {
    init {
        require(price > 0.0) { "price must be positive" }
        require(quantity > 0) { "quantity must be positive" }
    }
}

enum class LaorV4StrategyMode {
    NORMAL,
    REVERSE,
}

enum class LaorV4StrategySide {
    BUY,
    SELL,
}

enum class LaorV4StrategyOrderType {
    LOC,
    MOC,
    LIMIT,
}

enum class LaorV4StrategySymbol(
    val ticker: String,
    val targetPercent: Double,
) {
    TQQQ("TQQQ", 15.0),
    SOXL("SOXL", 20.0),
}

enum class LaorV4StrategyOrderTag {
    FIRST_BUY,
    STAR_HALF_BUY,
    AVG_HALF_BUY,
    STAR_FULL_BUY,
    QUARTER_SELL,
    TARGET_SELL,
    REVERSE_MOC_SELL,
    REVERSE_LOC_SELL,
    REVERSE_BUY,
}

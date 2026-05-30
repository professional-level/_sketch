package com.example.strategyexecutionservice.domain.strategy.laor

data class LaorV4StrategyConfig(
    val symbol: LaorV4StrategySymbol,
    val totalSplitCount: Int,
    val firstBuyLimitMultiplier: Double = DEFAULT_FIRST_BUY_LIMIT_MULTIPLIER,
) {
    init {
        require(totalSplitCount > 1) { "totalSplitCount must be greater than 1" }
        require(firstBuyLimitMultiplier > 1.0) { "firstBuyLimitMultiplier must be greater than 1" }
    }

    val reverseSellDivisionCount: Double
        get() = totalSplitCount / 2.0

    val reverseSellFactor: Double
        get() = 1.0 - 2.0 / totalSplitCount

    companion object {
        const val DEFAULT_FIRST_BUY_LIMIT_MULTIPLIER: Double = 1.12
    }
}

data class LaorV4StrategyState(
    val mode: LaorV4StrategyMode = LaorV4StrategyMode.NORMAL,
    val progressRound: Double = 0.0,
    val availableCash: Double,
    val holdingQuantity: Long = 0,
    val averagePurchasePrice: Double = 0.0,
    val realizedProfitLoss: Double = 0.0,
    val reverseModeElapsedDays: Int = 0,
) {
    init {
        require(progressRound >= 0.0) { "progressRound must not be negative" }
        require(availableCash >= 0.0) { "availableCash must not be negative" }
        require(holdingQuantity >= 0) { "holdingQuantity must not be negative" }
        require(averagePurchasePrice >= 0.0) { "averagePurchasePrice must not be negative" }
        require(holdingQuantity == 0L || averagePurchasePrice > 0.0) {
            "averagePurchasePrice must be positive when holdings exist"
        }
        require(reverseModeElapsedDays >= 0) { "reverseModeElapsedDays must not be negative" }
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
    val targetProfitPercent: Double,
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

package com.example.stockpurchaseservice.domain.strategy.execution

@JvmInline
value class StrategyExecutionId(val value: String) {
    init {
        require(value.isNotBlank()) { "strategyExecutionId must not be blank" }
    }
}

interface TradingStrategyExecution {
    val id: StrategyExecutionId
    val type: StrategyExecutionType
    val status: StrategyExecutionStatus

    fun generateOrders(market: StrategyMarketSnapshot): StrategyOrderPlan
    fun applyFills(fills: List<StrategyExecutionFill>, closePrice: Double): TradingStrategyExecution
}

enum class StrategyExecutionType {
    FINAL_PRICE_BATING_V1,
    INFINITE_BUY_V4,
}

enum class StrategyExecutionStatus {
    ACTIVE,
    CLOSED,
}

data class StrategyMarketSnapshot(
    val previousClose: Double,
    val recentClosePrices: List<Double> = emptyList(),
) {
    init {
        require(previousClose > 0.0) { "previousClose must be positive" }
        require(recentClosePrices.all { it > 0.0 }) { "recentClosePrices must be positive" }
    }
}

data class StrategyOrderPlan(
    val execution: TradingStrategyExecution,
    val orders: List<OrderIntent>,
)

data class OrderIntent(
    val executionId: StrategyExecutionId,
    val side: OrderSide,
    val type: OrderType,
    val price: Double?,
    val quantity: Long,
    val tag: String,
) {
    init {
        require(quantity > 0) { "quantity must be positive" }
        require(tag.isNotBlank()) { "tag must not be blank" }
        if (type == OrderType.MOC) {
            require(price == null || price > 0.0) { "MOC price must be null or positive" }
        } else {
            require(price != null && price > 0.0) { "$type price must be positive" }
        }
    }
}

data class StrategyExecutionFill(
    val side: OrderSide,
    val price: Double,
    val quantity: Long,
    val tag: String,
) {
    init {
        require(price > 0.0) { "price must be positive" }
        require(quantity > 0) { "quantity must be positive" }
        require(tag.isNotBlank()) { "tag must not be blank" }
    }
}

enum class OrderSide {
    BUY,
    SELL,
}

enum class OrderType {
    LOC,
    MOC,
    LIMIT,
}

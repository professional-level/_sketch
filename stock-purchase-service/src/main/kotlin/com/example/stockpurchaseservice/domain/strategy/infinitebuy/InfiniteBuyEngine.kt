package com.example.stockpurchaseservice.domain.strategy.infinitebuy

import kotlin.math.abs
import kotlin.math.floor

object InfiniteBuyEngine {
    private const val EPSILON = 0.0000001
    private const val QUARTER_SELL_RATIO = 0.25
    private const val REVERSE_BUY_CASH_RATIO = 0.25

    private val buyTags = setOf(
        OrderTag.FIRST_BUY,
        OrderTag.STAR_HALF_BUY,
        OrderTag.AVG_HALF_BUY,
        OrderTag.STAR_FULL_BUY,
        OrderTag.REVERSE_BUY,
    )

    private val sellTags = setOf(
        OrderTag.QUARTER_SELL,
        OrderTag.TARGET_SELL,
        OrderTag.REVERSE_MOC_SELL,
        OrderTag.REVERSE_LOC_SELL,
    )

    fun generateOrders(
        config: InfiniteBuyConfig,
        state: InfiniteBuyState,
        market: InfiniteBuyMarket,
    ): List<PlannedOrder> {
        return if (state.mode == TradeMode.REVERSE || shouldEnterReverse(config, state.t)) {
            generateReverseOrders(config, state, market)
        } else {
            generateNormalOrders(config, state, market)
        }
    }

    fun applyFills(
        config: InfiniteBuyConfig,
        state: InfiniteBuyState,
        fills: List<ExecutedFill>,
        closePrice: Double,
    ): InfiniteBuyState {
        require(closePrice > 0.0) { "closePrice must be positive" }

        val working = WorkingState.from(state)

        val sellFills = fills.filter { it.side == TradeSide.SELL }
        val buyFills = fills.filter { it.side == TradeSide.BUY }

        sellFills.forEach { fill ->
            require(fill.tag in sellTags) { "${fill.tag} is not a sell tag" }
        }

        buyFills.forEach { fill ->
            require(fill.tag in buyTags) { "${fill.tag} is not a buy tag" }
        }

        if (working.mode == TradeMode.REVERSE) {
            sellFills.forEach { fill -> applyReverseSell(config, working, fill) }
            buyFills.forEach { fill -> applyReverseBuy(config, working, fill) }
        } else {
            working.t = normalTAfterSells(working.t, working.shares, sellFills)
            sellFills.forEach { fill -> applySell(working, fill) }
            buyFills.forEach { fill -> applyNormalBuy(working, fill) }
        }

        if (working.shares == 0L) {
            return working.toState(
                mode = TradeMode.NORMAL,
                t = 0.0,
                avgPrice = 0.0,
                reverseDays = 0,
            )
        }

        if (working.mode == TradeMode.REVERSE) {
            val nextReverseDays = working.reverseDays + 1
            return if (shouldExitReverse(config, working.avgPrice, closePrice)) {
                working.toState(mode = TradeMode.NORMAL, reverseDays = 0)
            } else {
                working.toState(reverseDays = nextReverseDays)
            }
        }

        return if (shouldEnterReverse(config, working.t)) {
            working.toState(mode = TradeMode.REVERSE, reverseDays = 0)
        } else {
            working.toState()
        }
    }

    fun starPercentage(config: InfiniteBuyConfig, state: InfiniteBuyState): Double {
        return config.symbol.gridPercent * (1 - (2 * state.t / config.splits))
    }

    fun starPrice(config: InfiniteBuyConfig, state: InfiniteBuyState): Double {
        require(state.avgPrice > 0.0) { "avgPrice must be positive to calculate starPrice" }
        return state.avgPrice * (1 + starPercentage(config, state) / 100)
    }

    fun targetPrice(config: InfiniteBuyConfig, state: InfiniteBuyState): Double {
        require(state.avgPrice > 0.0) { "avgPrice must be positive to calculate targetPrice" }
        return state.avgPrice * (1 + config.symbol.gridPercent / 100)
    }

    fun oneBuyBudget(config: InfiniteBuyConfig, state: InfiniteBuyState): Double {
        val remainingBuys = config.splits - state.t
        return if (remainingBuys <= 0.0) {
            0.0
        } else {
            state.cash / remainingBuys
        }
    }

    fun reverseStarPrice(market: InfiniteBuyMarket): Double {
        require(market.recentClosePrices.size >= 5) {
            "at least 5 recentClosePrices are required for reverse star price"
        }
        return market.recentClosePrices.takeLast(5).average()
    }

    private fun generateNormalOrders(
        config: InfiniteBuyConfig,
        state: InfiniteBuyState,
        market: InfiniteBuyMarket,
    ): List<PlannedOrder> {
        if (state.shares == 0L) {
            val firstBuyPrice = market.previousClose * config.firstBuyMultiplier
            return listOfNotNull(
                buyOrder(
                    type = TradeOrderType.LOC,
                    price = firstBuyPrice,
                    budget = state.cash / config.splits,
                    tag = OrderTag.FIRST_BUY,
                ),
            )
        }

        val starPrice = starPrice(config, state)
        val starBuyPrice = starPrice - 0.01
        val oneBuyBudget = oneBuyBudget(config, state)
        val buyOrders = if (state.t < config.splits / 2.0) {
            listOfNotNull(
                buyOrder(
                    type = TradeOrderType.LOC,
                    price = starBuyPrice,
                    budget = oneBuyBudget / 2,
                    tag = OrderTag.STAR_HALF_BUY,
                ),
                buyOrder(
                    type = TradeOrderType.LOC,
                    price = state.avgPrice,
                    budget = oneBuyBudget / 2,
                    tag = OrderTag.AVG_HALF_BUY,
                ),
            )
        } else {
            listOfNotNull(
                buyOrder(
                    type = TradeOrderType.LOC,
                    price = starBuyPrice,
                    budget = oneBuyBudget,
                    tag = OrderTag.STAR_FULL_BUY,
                ),
            )
        }

        return buyOrders + normalSellOrders(config, state, starPrice)
    }

    private fun normalSellOrders(
        config: InfiniteBuyConfig,
        state: InfiniteBuyState,
        starPrice: Double,
    ): List<PlannedOrder> {
        val quarterSellQuantity = floor(state.shares * QUARTER_SELL_RATIO).toLong()
        val targetSellQuantity = state.shares - quarterSellQuantity
        return listOfNotNull(
            sellOrder(
                type = TradeOrderType.LOC,
                price = starPrice,
                quantity = quarterSellQuantity,
                tag = OrderTag.QUARTER_SELL,
            ),
            sellOrder(
                type = TradeOrderType.LIMIT,
                price = targetPrice(config, state),
                quantity = targetSellQuantity,
                tag = OrderTag.TARGET_SELL,
            ),
        )
    }

    private fun generateReverseOrders(
        config: InfiniteBuyConfig,
        state: InfiniteBuyState,
        market: InfiniteBuyMarket,
    ): List<PlannedOrder> {
        val sellQuantity = reverseSellQuantity(config, state.shares)
        if (state.reverseDays == 0) {
            return listOfNotNull(
                sellOrder(
                    type = TradeOrderType.MOC,
                    price = null,
                    quantity = sellQuantity,
                    tag = OrderTag.REVERSE_MOC_SELL,
                ),
            )
        }

        val reverseStarPrice = reverseStarPrice(market)
        val reverseBuyPrice = reverseStarPrice - 0.01
        return listOfNotNull(
            sellOrder(
                type = TradeOrderType.LOC,
                price = reverseStarPrice,
                quantity = sellQuantity,
                tag = OrderTag.REVERSE_LOC_SELL,
            ),
            buyOrder(
                type = TradeOrderType.LOC,
                price = reverseBuyPrice,
                budget = state.cash * REVERSE_BUY_CASH_RATIO,
                tag = OrderTag.REVERSE_BUY,
            ),
        )
    }

    private fun buyOrder(
        type: TradeOrderType,
        price: Double,
        budget: Double,
        tag: OrderTag,
    ): PlannedOrder? {
        val quantity = quantityForBudget(budget, price)
        return if (quantity > 0) {
            PlannedOrder(
                side = TradeSide.BUY,
                type = type,
                price = price,
                quantity = quantity,
                tag = tag,
            )
        } else {
            null
        }
    }

    private fun sellOrder(
        type: TradeOrderType,
        price: Double?,
        quantity: Long,
        tag: OrderTag,
    ): PlannedOrder? {
        return if (quantity > 0) {
            PlannedOrder(
                side = TradeSide.SELL,
                type = type,
                price = price,
                quantity = quantity,
                tag = tag,
            )
        } else {
            null
        }
    }

    private fun quantityForBudget(budget: Double, price: Double): Long {
        require(price > 0.0) { "price must be positive" }
        return floor((budget + EPSILON) / price).toLong()
    }

    private fun reverseSellQuantity(config: InfiniteBuyConfig, shares: Long): Long {
        return floor(shares / config.reverseSellDivisor).toLong()
    }

    private fun normalTAfterSells(
        t: Double,
        startingShares: Long,
        sellFills: List<ExecutedFill>,
    ): Double {
        if (sellFills.isEmpty()) return t

        val soldShares = sellFills.sumOf { it.quantity }
        if (soldShares >= startingShares) return 0.0

        val targetSellFilled = sellFills.any { it.tag == OrderTag.TARGET_SELL }
        val quarterSellFilled = sellFills.any { it.tag == OrderTag.QUARTER_SELL }

        return cleanZero(
            when {
                targetSellFilled -> t * 0.25
                quarterSellFilled -> t * 0.75
                else -> t
            },
        )
    }

    private fun applyReverseSell(
        config: InfiniteBuyConfig,
        state: WorkingState,
        fill: ExecutedFill,
    ) {
        if (fill.tag == OrderTag.REVERSE_MOC_SELL || fill.tag == OrderTag.REVERSE_LOC_SELL) {
            state.t = cleanZero(state.t * config.reverseSellFactor)
        }
        applySell(state, fill)
    }

    private fun applyNormalBuy(state: WorkingState, fill: ExecutedFill) {
        applyBuy(state, fill)
        state.t = cleanZero(state.t + normalBuyTIncrement(fill.tag))
    }

    private fun applyReverseBuy(
        config: InfiniteBuyConfig,
        state: WorkingState,
        fill: ExecutedFill,
    ) {
        state.t = cleanZero(state.t + (config.splits - state.t) * REVERSE_BUY_CASH_RATIO)
        applyBuy(state, fill)
    }

    private fun applySell(state: WorkingState, fill: ExecutedFill) {
        require(fill.quantity <= state.shares) {
            "sell quantity ${fill.quantity} exceeds held shares ${state.shares}"
        }

        state.cash = cleanZero(state.cash + fill.price * fill.quantity)
        state.realizedPnl = cleanZero(state.realizedPnl + (fill.price - state.avgPrice) * fill.quantity)
        state.shares -= fill.quantity

        if (state.shares == 0L) {
            state.avgPrice = 0.0
            state.t = 0.0
            state.mode = TradeMode.NORMAL
            state.reverseDays = 0
        }
    }

    private fun applyBuy(state: WorkingState, fill: ExecutedFill) {
        val cost = fill.price * fill.quantity
        require(cost <= state.cash + EPSILON) {
            "buy cost $cost exceeds cash ${state.cash}"
        }

        val previousPositionValue = state.avgPrice * state.shares
        val nextShares = state.shares + fill.quantity
        state.cash = cleanZero(state.cash - cost)
        state.avgPrice = cleanZero((previousPositionValue + cost) / nextShares)
        state.shares = nextShares
    }

    private fun normalBuyTIncrement(tag: OrderTag): Double {
        return when (tag) {
            OrderTag.FIRST_BUY,
            OrderTag.STAR_FULL_BUY -> 1.0

            OrderTag.STAR_HALF_BUY,
            OrderTag.AVG_HALF_BUY -> 0.5

            OrderTag.QUARTER_SELL,
            OrderTag.TARGET_SELL,
            OrderTag.REVERSE_MOC_SELL,
            OrderTag.REVERSE_LOC_SELL,
            OrderTag.REVERSE_BUY -> 0.0
        }
    }

    private fun shouldEnterReverse(config: InfiniteBuyConfig, t: Double): Boolean {
        return t > config.splits - 1
    }

    private fun shouldExitReverse(
        config: InfiniteBuyConfig,
        avgPrice: Double,
        closePrice: Double,
    ): Boolean {
        val reverseExitPrice = avgPrice * (1 - config.symbol.gridPercent / 100)
        return closePrice > reverseExitPrice
    }

    private fun cleanZero(value: Double): Double {
        return if (abs(value) < EPSILON) 0.0 else value
    }

    private class WorkingState(
        var mode: TradeMode,
        var t: Double,
        var cash: Double,
        var shares: Long,
        var avgPrice: Double,
        var realizedPnl: Double,
        var reverseDays: Int,
    ) {
        fun toState(
            mode: TradeMode = this.mode,
            t: Double = this.t,
            cash: Double = this.cash,
            shares: Long = this.shares,
            avgPrice: Double = this.avgPrice,
            realizedPnl: Double = this.realizedPnl,
            reverseDays: Int = this.reverseDays,
        ): InfiniteBuyState {
            return InfiniteBuyState(
                mode = mode,
                t = t,
                cash = cash,
                shares = shares,
                avgPrice = avgPrice,
                realizedPnl = realizedPnl,
                reverseDays = reverseDays,
            )
        }

        companion object {
            fun from(state: InfiniteBuyState): WorkingState {
                return WorkingState(
                    mode = state.mode,
                    t = state.t,
                    cash = state.cash,
                    shares = state.shares,
                    avgPrice = state.avgPrice,
                    realizedPnl = state.realizedPnl,
                    reverseDays = state.reverseDays,
                )
            }
        }
    }
}

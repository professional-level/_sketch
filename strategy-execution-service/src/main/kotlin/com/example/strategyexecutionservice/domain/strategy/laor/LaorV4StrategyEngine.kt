package com.example.strategyexecutionservice.domain.strategy.laor

import kotlin.math.abs
import kotlin.math.floor

object LaorV4StrategyEngine {
    private const val EPSILON = 0.0000001
    private const val QUARTER_SELL_RATIO = 0.25
    private const val REVERSE_BUY_CASH_RATIO = 0.25

    private val buyTags = setOf(
        LaorV4StrategyOrderTag.FIRST_BUY,
        LaorV4StrategyOrderTag.STAR_HALF_BUY,
        LaorV4StrategyOrderTag.AVG_HALF_BUY,
        LaorV4StrategyOrderTag.STAR_FULL_BUY,
        LaorV4StrategyOrderTag.REVERSE_BUY,
    )

    private val sellTags = setOf(
        LaorV4StrategyOrderTag.QUARTER_SELL,
        LaorV4StrategyOrderTag.TARGET_SELL,
        LaorV4StrategyOrderTag.REVERSE_MOC_SELL,
        LaorV4StrategyOrderTag.REVERSE_LOC_SELL,
    )

    fun generateOrders(
        config: LaorV4StrategyConfig,
        state: LaorV4StrategyState,
        market: LaorV4StrategyMarket,
    ): List<LaorV4StrategyOrder> {
        return if (state.mode == LaorV4StrategyMode.REVERSE || shouldEnterReverse(config, state.t)) {
            generateReverseOrders(config, state, market)
        } else {
            generateNormalOrders(config, state, market)
        }
    }

    fun applyFills(
        config: LaorV4StrategyConfig,
        state: LaorV4StrategyState,
        fills: List<LaorV4StrategyFill>,
        closePrice: Double,
    ): LaorV4StrategyState {
        require(closePrice > 0.0) { "closePrice must be positive" }

        val working = WorkingState.from(state)
        val sellFills = fills.filter { it.side == LaorV4StrategySide.SELL }
        val buyFills = fills.filter { it.side == LaorV4StrategySide.BUY }

        sellFills.forEach { fill ->
            require(fill.tag in sellTags) { "${fill.tag} is not a sell tag" }
        }

        buyFills.forEach { fill ->
            require(fill.tag in buyTags) { "${fill.tag} is not a buy tag" }
        }

        if (working.mode == LaorV4StrategyMode.REVERSE) {
            sellFills.forEach { fill -> applyReverseSell(config, working, fill) }
            buyFills.forEach { fill -> applyReverseBuy(config, working, fill) }
        } else {
            working.t = normalTAfterSells(working.t, working.shares, sellFills)
            sellFills.forEach { fill -> applySell(working, fill) }
            buyFills.forEach { fill -> applyNormalBuy(working, fill) }
        }

        if (working.shares == 0L) {
            return working.toState(
                mode = LaorV4StrategyMode.NORMAL,
                t = 0.0,
                avgPrice = 0.0,
                reverseDays = 0,
            )
        }

        if (working.mode == LaorV4StrategyMode.REVERSE) {
            val nextReverseDays = working.reverseDays + 1
            return if (shouldExitReverse(config, working.avgPrice, closePrice)) {
                working.toState(mode = LaorV4StrategyMode.NORMAL, reverseDays = 0)
            } else {
                working.toState(reverseDays = nextReverseDays)
            }
        }

        return if (shouldEnterReverse(config, working.t)) {
            working.toState(mode = LaorV4StrategyMode.REVERSE, reverseDays = 0)
        } else {
            working.toState()
        }
    }

    fun starPercentage(config: LaorV4StrategyConfig, state: LaorV4StrategyState): Double {
        return config.symbol.targetPercent * (1 - (2 * state.t / config.splits))
    }

    fun starPrice(config: LaorV4StrategyConfig, state: LaorV4StrategyState): Double {
        require(state.avgPrice > 0.0) { "avgPrice must be positive to calculate starPrice" }
        return state.avgPrice * (1 + starPercentage(config, state) / 100)
    }

    fun targetPrice(config: LaorV4StrategyConfig, state: LaorV4StrategyState): Double {
        require(state.avgPrice > 0.0) { "avgPrice must be positive to calculate targetPrice" }
        return state.avgPrice * (1 + config.symbol.targetPercent / 100)
    }

    fun oneBuyBudget(config: LaorV4StrategyConfig, state: LaorV4StrategyState): Double {
        val remainingBuys = config.splits - state.t
        return if (remainingBuys <= 0.0) {
            0.0
        } else {
            state.cash / remainingBuys
        }
    }

    fun reverseStarPrice(market: LaorV4StrategyMarket): Double {
        require(market.recentClosePrices.size >= 5) {
            "at least 5 recentClosePrices are required for reverse star price"
        }
        return market.recentClosePrices.takeLast(5).average()
    }

    private fun generateNormalOrders(
        config: LaorV4StrategyConfig,
        state: LaorV4StrategyState,
        market: LaorV4StrategyMarket,
    ): List<LaorV4StrategyOrder> {
        if (state.shares == 0L) {
            val firstBuyPrice = market.previousClose * config.firstBuyMultiplier
            return listOfNotNull(
                buyOrder(
                    type = LaorV4StrategyOrderType.LOC,
                    price = firstBuyPrice,
                    budget = state.cash / config.splits,
                    tag = LaorV4StrategyOrderTag.FIRST_BUY,
                ),
            )
        }

        val starPrice = starPrice(config, state)
        val starBuyPrice = starPrice - 0.01
        val oneBuyBudget = oneBuyBudget(config, state)
        val buyOrders = if (state.t < config.splits / 2.0) {
            listOfNotNull(
                buyOrder(
                    type = LaorV4StrategyOrderType.LOC,
                    price = starBuyPrice,
                    budget = oneBuyBudget / 2,
                    tag = LaorV4StrategyOrderTag.STAR_HALF_BUY,
                ),
                buyOrder(
                    type = LaorV4StrategyOrderType.LOC,
                    price = state.avgPrice,
                    budget = oneBuyBudget / 2,
                    tag = LaorV4StrategyOrderTag.AVG_HALF_BUY,
                ),
            )
        } else {
            listOfNotNull(
                buyOrder(
                    type = LaorV4StrategyOrderType.LOC,
                    price = starBuyPrice,
                    budget = oneBuyBudget,
                    tag = LaorV4StrategyOrderTag.STAR_FULL_BUY,
                ),
            )
        }

        return buyOrders + normalSellOrders(config, state, starPrice)
    }

    private fun normalSellOrders(
        config: LaorV4StrategyConfig,
        state: LaorV4StrategyState,
        starPrice: Double,
    ): List<LaorV4StrategyOrder> {
        val quarterSellQuantity = floor(state.shares * QUARTER_SELL_RATIO).toLong()
        val targetSellQuantity = state.shares - quarterSellQuantity
        return listOfNotNull(
            sellOrder(
                type = LaorV4StrategyOrderType.LOC,
                price = starPrice,
                quantity = quarterSellQuantity,
                tag = LaorV4StrategyOrderTag.QUARTER_SELL,
            ),
            sellOrder(
                type = LaorV4StrategyOrderType.LIMIT,
                price = targetPrice(config, state),
                quantity = targetSellQuantity,
                tag = LaorV4StrategyOrderTag.TARGET_SELL,
            ),
        )
    }

    private fun generateReverseOrders(
        config: LaorV4StrategyConfig,
        state: LaorV4StrategyState,
        market: LaorV4StrategyMarket,
    ): List<LaorV4StrategyOrder> {
        val sellQuantity = reverseSellQuantity(config, state.shares)
        if (state.reverseDays == 0) {
            return listOfNotNull(
                sellOrder(
                    type = LaorV4StrategyOrderType.MOC,
                    price = null,
                    quantity = sellQuantity,
                    tag = LaorV4StrategyOrderTag.REVERSE_MOC_SELL,
                ),
            )
        }

        val reverseStarPrice = reverseStarPrice(market)
        val reverseBuyPrice = reverseStarPrice - 0.01
        return listOfNotNull(
            sellOrder(
                type = LaorV4StrategyOrderType.LOC,
                price = reverseStarPrice,
                quantity = sellQuantity,
                tag = LaorV4StrategyOrderTag.REVERSE_LOC_SELL,
            ),
            buyOrder(
                type = LaorV4StrategyOrderType.LOC,
                price = reverseBuyPrice,
                budget = state.cash * REVERSE_BUY_CASH_RATIO,
                tag = LaorV4StrategyOrderTag.REVERSE_BUY,
            ),
        )
    }

    private fun buyOrder(
        type: LaorV4StrategyOrderType,
        price: Double,
        budget: Double,
        tag: LaorV4StrategyOrderTag,
    ): LaorV4StrategyOrder? {
        val quantity = quantityForBudget(budget, price)
        return if (quantity > 0) {
            LaorV4StrategyOrder(
                side = LaorV4StrategySide.BUY,
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
        type: LaorV4StrategyOrderType,
        price: Double?,
        quantity: Long,
        tag: LaorV4StrategyOrderTag,
    ): LaorV4StrategyOrder? {
        return if (quantity > 0) {
            LaorV4StrategyOrder(
                side = LaorV4StrategySide.SELL,
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

    private fun reverseSellQuantity(config: LaorV4StrategyConfig, shares: Long): Long {
        return floor(shares / config.reverseSellDivisor).toLong()
    }

    private fun normalTAfterSells(
        t: Double,
        startingShares: Long,
        sellFills: List<LaorV4StrategyFill>,
    ): Double {
        if (sellFills.isEmpty()) return t

        val soldShares = sellFills.sumOf { it.quantity }
        if (soldShares >= startingShares) return 0.0

        val targetSellFilled = sellFills.any { it.tag == LaorV4StrategyOrderTag.TARGET_SELL }
        val quarterSellFilled = sellFills.any { it.tag == LaorV4StrategyOrderTag.QUARTER_SELL }

        return cleanZero(
            when {
                targetSellFilled -> t * 0.25
                quarterSellFilled -> t * 0.75
                else -> t
            },
        )
    }

    private fun applyReverseSell(
        config: LaorV4StrategyConfig,
        state: WorkingState,
        fill: LaorV4StrategyFill,
    ) {
        if (fill.tag == LaorV4StrategyOrderTag.REVERSE_MOC_SELL ||
            fill.tag == LaorV4StrategyOrderTag.REVERSE_LOC_SELL
        ) {
            state.t = cleanZero(state.t * config.reverseSellFactor)
        }
        applySell(state, fill)
    }

    private fun applyNormalBuy(state: WorkingState, fill: LaorV4StrategyFill) {
        applyBuy(state, fill)
        state.t = cleanZero(state.t + normalBuyTIncrement(fill.tag))
    }

    private fun applyReverseBuy(
        config: LaorV4StrategyConfig,
        state: WorkingState,
        fill: LaorV4StrategyFill,
    ) {
        state.t = cleanZero(state.t + (config.splits - state.t) * REVERSE_BUY_CASH_RATIO)
        applyBuy(state, fill)
    }

    private fun applySell(state: WorkingState, fill: LaorV4StrategyFill) {
        require(fill.quantity <= state.shares) {
            "sell quantity ${fill.quantity} exceeds held shares ${state.shares}"
        }

        state.cash = cleanZero(state.cash + fill.price * fill.quantity)
        state.realizedPnl = cleanZero(state.realizedPnl + (fill.price - state.avgPrice) * fill.quantity)
        state.shares -= fill.quantity

        if (state.shares == 0L) {
            state.avgPrice = 0.0
            state.t = 0.0
            state.mode = LaorV4StrategyMode.NORMAL
            state.reverseDays = 0
        }
    }

    private fun applyBuy(state: WorkingState, fill: LaorV4StrategyFill) {
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

    private fun normalBuyTIncrement(tag: LaorV4StrategyOrderTag): Double {
        return when (tag) {
            LaorV4StrategyOrderTag.FIRST_BUY,
            LaorV4StrategyOrderTag.STAR_FULL_BUY -> 1.0

            LaorV4StrategyOrderTag.STAR_HALF_BUY,
            LaorV4StrategyOrderTag.AVG_HALF_BUY -> 0.5

            LaorV4StrategyOrderTag.QUARTER_SELL,
            LaorV4StrategyOrderTag.TARGET_SELL,
            LaorV4StrategyOrderTag.REVERSE_MOC_SELL,
            LaorV4StrategyOrderTag.REVERSE_LOC_SELL,
            LaorV4StrategyOrderTag.REVERSE_BUY -> 0.0
        }
    }

    private fun shouldEnterReverse(config: LaorV4StrategyConfig, t: Double): Boolean {
        return t > config.splits - 1
    }

    private fun shouldExitReverse(
        config: LaorV4StrategyConfig,
        avgPrice: Double,
        closePrice: Double,
    ): Boolean {
        val reverseExitPrice = avgPrice * (1 - config.symbol.targetPercent / 100)
        return closePrice > reverseExitPrice
    }

    private fun cleanZero(value: Double): Double {
        return if (abs(value) < EPSILON) 0.0 else value
    }

    private class WorkingState(
        var mode: LaorV4StrategyMode,
        var t: Double,
        var cash: Double,
        var shares: Long,
        var avgPrice: Double,
        var realizedPnl: Double,
        var reverseDays: Int,
    ) {
        fun toState(
            mode: LaorV4StrategyMode = this.mode,
            t: Double = this.t,
            cash: Double = this.cash,
            shares: Long = this.shares,
            avgPrice: Double = this.avgPrice,
            realizedPnl: Double = this.realizedPnl,
            reverseDays: Int = this.reverseDays,
        ): LaorV4StrategyState {
            return LaorV4StrategyState(
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
            fun from(state: LaorV4StrategyState): WorkingState {
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

package com.example.strategyexecutionservice.domain.strategy.laor

import kotlin.math.abs
import kotlin.math.floor

object LaorV4StrategyEngine {
    private const val EPSILON = 0.0000001
    private const val QUARTER_SELL_RATIO = 0.25
    private const val REVERSE_BUY_AVAILABLE_CASH_RATIO = 0.25

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
        return if (state.mode == LaorV4StrategyMode.REVERSE || shouldEnterReverse(config, state.progressRound)) {
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
            working.progressRound = normalProgressRoundAfterSells(
                progressRound = working.progressRound,
                startingHoldingQuantity = working.holdingQuantity,
                sellFills = sellFills,
            )
            sellFills.forEach { fill -> applySell(working, fill) }
            buyFills.forEach { fill -> applyNormalBuy(working, fill) }
        }

        if (working.holdingQuantity == 0L) {
            return working.toState(
                mode = LaorV4StrategyMode.NORMAL,
                progressRound = 0.0,
                averagePurchasePrice = 0.0,
                reverseModeElapsedDays = 0,
            )
        }

        if (working.mode == LaorV4StrategyMode.REVERSE) {
            val nextReverseModeElapsedDays = working.reverseModeElapsedDays + 1
            return if (shouldExitReverse(config, working.averagePurchasePrice, closePrice)) {
                working.toState(mode = LaorV4StrategyMode.NORMAL, reverseModeElapsedDays = 0)
            } else {
                working.toState(reverseModeElapsedDays = nextReverseModeElapsedDays)
            }
        }

        return if (shouldEnterReverse(config, working.progressRound)) {
            working.toState(mode = LaorV4StrategyMode.REVERSE, reverseModeElapsedDays = 0)
        } else {
            working.toState()
        }
    }

    fun normalModeStarProfitPercent(config: LaorV4StrategyConfig, state: LaorV4StrategyState): Double {
        return config.symbol.targetProfitPercent * (1 - (2 * state.progressRound / config.totalSplitCount))
    }

    fun normalModeStarPrice(config: LaorV4StrategyConfig, state: LaorV4StrategyState): Double {
        require(state.averagePurchasePrice > 0.0) {
            "averagePurchasePrice must be positive to calculate normalModeStarPrice"
        }
        return state.averagePurchasePrice * (1 + normalModeStarProfitPercent(config, state) / 100)
    }

    fun targetSellPrice(config: LaorV4StrategyConfig, state: LaorV4StrategyState): Double {
        require(state.averagePurchasePrice > 0.0) {
            "averagePurchasePrice must be positive to calculate targetSellPrice"
        }
        return state.averagePurchasePrice * (1 + config.symbol.targetProfitPercent / 100)
    }

    fun singleBuyBudget(config: LaorV4StrategyConfig, state: LaorV4StrategyState): Double {
        val remainingBuyRoundCount = config.totalSplitCount - state.progressRound
        return if (remainingBuyRoundCount <= 0.0) {
            0.0
        } else {
            state.availableCash / remainingBuyRoundCount
        }
    }

    fun reverseModeStarPrice(market: LaorV4StrategyMarket): Double {
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
        if (state.holdingQuantity == 0L) {
            val firstBuyLimitPrice = market.previousClose * config.firstBuyLimitMultiplier
            return listOfNotNull(
                buyOrder(
                    type = LaorV4StrategyOrderType.LOC,
                    price = firstBuyLimitPrice,
                    budget = state.availableCash / config.totalSplitCount,
                    tag = LaorV4StrategyOrderTag.FIRST_BUY,
                ),
            )
        }

        val starPrice = normalModeStarPrice(config, state)
        val starBuyPrice = starPrice - 0.01
        val singleBuyBudget = singleBuyBudget(config, state)
        val buyOrders = if (state.progressRound < config.totalSplitCount / 2.0) {
            listOfNotNull(
                buyOrder(
                    type = LaorV4StrategyOrderType.LOC,
                    price = starBuyPrice,
                    budget = singleBuyBudget / 2,
                    tag = LaorV4StrategyOrderTag.STAR_HALF_BUY,
                ),
                buyOrder(
                    type = LaorV4StrategyOrderType.LOC,
                    price = state.averagePurchasePrice,
                    budget = singleBuyBudget / 2,
                    tag = LaorV4StrategyOrderTag.AVG_HALF_BUY,
                ),
            )
        } else {
            listOfNotNull(
                buyOrder(
                    type = LaorV4StrategyOrderType.LOC,
                    price = starBuyPrice,
                    budget = singleBuyBudget,
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
        val quarterSellQuantity = floor(state.holdingQuantity * QUARTER_SELL_RATIO).toLong()
        val targetSellQuantity = state.holdingQuantity - quarterSellQuantity
        return listOfNotNull(
            sellOrder(
                type = LaorV4StrategyOrderType.LOC,
                price = starPrice,
                quantity = quarterSellQuantity,
                tag = LaorV4StrategyOrderTag.QUARTER_SELL,
            ),
            sellOrder(
                type = LaorV4StrategyOrderType.LIMIT,
                price = targetSellPrice(config, state),
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
        val sellQuantity = reverseSellQuantity(config, state.holdingQuantity)
        if (state.reverseModeElapsedDays == 0) {
            return listOfNotNull(
                sellOrder(
                    type = LaorV4StrategyOrderType.MOC,
                    price = null,
                    quantity = sellQuantity,
                    tag = LaorV4StrategyOrderTag.REVERSE_MOC_SELL,
                ),
            )
        }

        val reverseModeStarPrice = reverseModeStarPrice(market)
        val reverseBuyPrice = reverseModeStarPrice - 0.01
        return listOfNotNull(
            sellOrder(
                type = LaorV4StrategyOrderType.LOC,
                price = reverseModeStarPrice,
                quantity = sellQuantity,
                tag = LaorV4StrategyOrderTag.REVERSE_LOC_SELL,
            ),
            buyOrder(
                type = LaorV4StrategyOrderType.LOC,
                price = reverseBuyPrice,
                budget = state.availableCash * REVERSE_BUY_AVAILABLE_CASH_RATIO,
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

    private fun reverseSellQuantity(config: LaorV4StrategyConfig, holdingQuantity: Long): Long {
        return floor(holdingQuantity / config.reverseSellDivisionCount).toLong()
    }

    private fun normalProgressRoundAfterSells(
        progressRound: Double,
        startingHoldingQuantity: Long,
        sellFills: List<LaorV4StrategyFill>,
    ): Double {
        if (sellFills.isEmpty()) return progressRound

        val soldQuantity = sellFills.sumOf { it.quantity }
        if (soldQuantity >= startingHoldingQuantity) return 0.0

        val targetSellFilled = sellFills.any { it.tag == LaorV4StrategyOrderTag.TARGET_SELL }
        val quarterSellFilled = sellFills.any { it.tag == LaorV4StrategyOrderTag.QUARTER_SELL }

        return cleanZero(
            when {
                targetSellFilled -> progressRound * 0.25
                quarterSellFilled -> progressRound * 0.75
                else -> progressRound
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
            state.progressRound = cleanZero(state.progressRound * config.reverseSellFactor)
        }
        applySell(state, fill)
    }

    private fun applyNormalBuy(state: WorkingState, fill: LaorV4StrategyFill) {
        applyBuy(state, fill)
        state.progressRound = cleanZero(state.progressRound + normalBuyProgressRoundIncrement(fill.tag))
    }

    private fun applyReverseBuy(
        config: LaorV4StrategyConfig,
        state: WorkingState,
        fill: LaorV4StrategyFill,
    ) {
        state.progressRound = cleanZero(
            state.progressRound +
                (config.totalSplitCount - state.progressRound) * REVERSE_BUY_AVAILABLE_CASH_RATIO,
        )
        applyBuy(state, fill)
    }

    private fun applySell(state: WorkingState, fill: LaorV4StrategyFill) {
        require(fill.quantity <= state.holdingQuantity) {
            "sell quantity ${fill.quantity} exceeds holdingQuantity ${state.holdingQuantity}"
        }

        state.availableCash = cleanZero(state.availableCash + fill.price * fill.quantity)
        state.realizedProfitLoss = cleanZero(
            state.realizedProfitLoss + (fill.price - state.averagePurchasePrice) * fill.quantity,
        )
        state.holdingQuantity -= fill.quantity

        if (state.holdingQuantity == 0L) {
            state.averagePurchasePrice = 0.0
            state.progressRound = 0.0
            state.mode = LaorV4StrategyMode.NORMAL
            state.reverseModeElapsedDays = 0
        }
    }

    private fun applyBuy(state: WorkingState, fill: LaorV4StrategyFill) {
        val cost = fill.price * fill.quantity
        require(cost <= state.availableCash + EPSILON) {
            "buy cost $cost exceeds availableCash ${state.availableCash}"
        }

        val previousPositionValue = state.averagePurchasePrice * state.holdingQuantity
        val nextHoldingQuantity = state.holdingQuantity + fill.quantity
        state.availableCash = cleanZero(state.availableCash - cost)
        state.averagePurchasePrice = cleanZero((previousPositionValue + cost) / nextHoldingQuantity)
        state.holdingQuantity = nextHoldingQuantity
    }

    private fun normalBuyProgressRoundIncrement(tag: LaorV4StrategyOrderTag): Double {
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

    private fun shouldEnterReverse(config: LaorV4StrategyConfig, progressRound: Double): Boolean {
        return progressRound > config.totalSplitCount - 1
    }

    private fun shouldExitReverse(
        config: LaorV4StrategyConfig,
        averagePurchasePrice: Double,
        closePrice: Double,
    ): Boolean {
        val reverseExitPrice = averagePurchasePrice * (1 - config.symbol.targetProfitPercent / 100)
        return closePrice > reverseExitPrice
    }

    private fun cleanZero(value: Double): Double {
        return if (abs(value) < EPSILON) 0.0 else value
    }

    private class WorkingState(
        var mode: LaorV4StrategyMode,
        var progressRound: Double,
        var availableCash: Double,
        var holdingQuantity: Long,
        var averagePurchasePrice: Double,
        var realizedProfitLoss: Double,
        var reverseModeElapsedDays: Int,
    ) {
        fun toState(
            mode: LaorV4StrategyMode = this.mode,
            progressRound: Double = this.progressRound,
            availableCash: Double = this.availableCash,
            holdingQuantity: Long = this.holdingQuantity,
            averagePurchasePrice: Double = this.averagePurchasePrice,
            realizedProfitLoss: Double = this.realizedProfitLoss,
            reverseModeElapsedDays: Int = this.reverseModeElapsedDays,
        ): LaorV4StrategyState {
            return LaorV4StrategyState(
                mode = mode,
                progressRound = progressRound,
                availableCash = availableCash,
                holdingQuantity = holdingQuantity,
                averagePurchasePrice = averagePurchasePrice,
                realizedProfitLoss = realizedProfitLoss,
                reverseModeElapsedDays = reverseModeElapsedDays,
            )
        }

        companion object {
            fun from(state: LaorV4StrategyState): WorkingState {
                return WorkingState(
                    mode = state.mode,
                    progressRound = state.progressRound,
                    availableCash = state.availableCash,
                    holdingQuantity = state.holdingQuantity,
                    averagePurchasePrice = state.averagePurchasePrice,
                    realizedProfitLoss = state.realizedProfitLoss,
                    reverseModeElapsedDays = state.reverseModeElapsedDays,
                )
            }
        }
    }
}

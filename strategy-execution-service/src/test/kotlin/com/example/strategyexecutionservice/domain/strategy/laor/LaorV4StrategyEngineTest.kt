package com.example.strategyexecutionservice.domain.strategy.laor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LaorV4StrategyEngineTest {

    @Test
    fun `rejects first buy multiplier at or below one`() {
        assertFailsWith<IllegalArgumentException> {
            LaorV4StrategyConfig(
                symbol = LaorV4StrategySymbol.TQQQ,
                totalSplitCount = 20,
                firstBuyLimitMultiplier = 1.0,
            )
        }
    }

    @Test
    fun `calculates star profit percent for supported Laor strategy symbols and split counts`() {
        val cases = listOf(
            CalculationCase(LaorV4StrategySymbol.TQQQ, 20, 0.0, 15.0),
            CalculationCase(LaorV4StrategySymbol.TQQQ, 20, 10.0, 0.0),
            CalculationCase(LaorV4StrategySymbol.TQQQ, 30, 7.5, 7.5),
            CalculationCase(LaorV4StrategySymbol.SOXL, 20, 10.0, 0.0),
            CalculationCase(LaorV4StrategySymbol.SOXL, 40, 10.0, 10.0),
        )

        cases.forEach { case ->
            val config = LaorV4StrategyConfig(symbol = case.symbol, totalSplitCount = case.totalSplitCount)
            val state = LaorV4StrategyState(progressRound = case.progressRound, availableCash = 0.0)

            assertDouble(case.expected, LaorV4StrategyEngine.normalModeStarProfitPercent(config, state))
        }
    }

    @Test
    fun `plans first-half buys with star buy below star sell price`() {
        val config = LaorV4StrategyConfig(symbol = LaorV4StrategySymbol.TQQQ, totalSplitCount = 20)
        val state = LaorV4StrategyState(
            progressRound = 4.0,
            availableCash = 32_000.0,
            holdingQuantity = 100,
            averagePurchasePrice = 100.0,
        )

        val orders = LaorV4StrategyEngine.generateOrders(
            config = config,
            state = state,
            market = LaorV4StrategyMarket(previousClose = 90.0),
        )

        assertEquals(4, orders.size)
        assertOrder(orders[0], LaorV4StrategySide.BUY, LaorV4StrategyOrderType.LOC, 108.99, 9, LaorV4StrategyOrderTag.STAR_HALF_BUY)
        assertOrder(orders[1], LaorV4StrategySide.BUY, LaorV4StrategyOrderType.LOC, 100.0, 10, LaorV4StrategyOrderTag.AVG_HALF_BUY)
        assertOrder(orders[2], LaorV4StrategySide.SELL, LaorV4StrategyOrderType.LOC, 109.0, 25, LaorV4StrategyOrderTag.QUARTER_SELL)
        assertOrder(orders[3], LaorV4StrategySide.SELL, LaorV4StrategyOrderType.LIMIT, 115.0, 75, LaorV4StrategyOrderTag.TARGET_SELL)
    }

    @Test
    fun `plans first reverse day MOC sell`() {
        val config = LaorV4StrategyConfig(symbol = LaorV4StrategySymbol.TQQQ, totalSplitCount = 20)
        val state = LaorV4StrategyState(
            mode = LaorV4StrategyMode.REVERSE,
            progressRound = 20.0,
            availableCash = 2_000.0,
            holdingQuantity = 200,
            averagePurchasePrice = 100.0,
        )

        val orders = LaorV4StrategyEngine.generateOrders(
            config = config,
            state = state,
            market = LaorV4StrategyMarket(previousClose = 80.0),
        )

        assertEquals(1, orders.size)
        assertEquals(LaorV4StrategySide.SELL, orders[0].side)
        assertEquals(LaorV4StrategyOrderType.MOC, orders[0].type)
        assertNull(orders[0].price)
        assertEquals(20, orders[0].quantity)
        assertEquals(LaorV4StrategyOrderTag.REVERSE_MOC_SELL, orders[0].tag)
    }

    @Test
    fun `plans later reverse sell and available-cash-quarter buy from five-day average`() {
        val config = LaorV4StrategyConfig(symbol = LaorV4StrategySymbol.TQQQ, totalSplitCount = 20)
        val state = LaorV4StrategyState(
            mode = LaorV4StrategyMode.REVERSE,
            progressRound = 18.0,
            availableCash = 10_000.0,
            holdingQuantity = 180,
            averagePurchasePrice = 100.0,
            reverseModeElapsedDays = 1,
        )

        val orders = LaorV4StrategyEngine.generateOrders(
            config = config,
            state = state,
            market = LaorV4StrategyMarket(
                previousClose = 86.0,
                recentClosePrices = listOf(78.0, 80.0, 82.0, 84.0, 86.0),
            ),
        )

        assertEquals(2, orders.size)
        assertOrder(orders[0], LaorV4StrategySide.SELL, LaorV4StrategyOrderType.LOC, 82.0, 18, LaorV4StrategyOrderTag.REVERSE_LOC_SELL)
        assertOrder(orders[1], LaorV4StrategySide.BUY, LaorV4StrategyOrderType.LOC, 81.99, 30, LaorV4StrategyOrderTag.REVERSE_BUY)
    }

    @Test
    fun `target sell followed by half buy keeps quarter T and adds buy increment`() {
        val config = LaorV4StrategyConfig(symbol = LaorV4StrategySymbol.TQQQ, totalSplitCount = 20)
        val state = LaorV4StrategyState(
            progressRound = 4.0,
            availableCash = 1_000.0,
            holdingQuantity = 100,
            averagePurchasePrice = 100.0,
        )

        val next = LaorV4StrategyEngine.applyFills(
            config = config,
            state = state,
            fills = listOf(
                LaorV4StrategyFill(LaorV4StrategySide.SELL, 115.0, 75, LaorV4StrategyOrderTag.TARGET_SELL),
                LaorV4StrategyFill(LaorV4StrategySide.BUY, 90.0, 10, LaorV4StrategyOrderTag.STAR_HALF_BUY),
            ),
            closePrice = 100.0,
        )

        assertEquals(LaorV4StrategyMode.NORMAL, next.mode)
        assertDouble(1.5, next.progressRound)
        assertDouble(8_725.0, next.availableCash)
        assertEquals(35, next.holdingQuantity)
        assertDouble(97.1428571429, next.averagePurchasePrice)
        assertDouble(1_125.0, next.realizedProfitLoss)
    }

    @Test
    fun `reverse buy and sell fills update T by Laor reverse formula`() {
        val config = LaorV4StrategyConfig(symbol = LaorV4StrategySymbol.TQQQ, totalSplitCount = 20)
        val state = LaorV4StrategyState(
            mode = LaorV4StrategyMode.REVERSE,
            progressRound = 20.0,
            availableCash = 10_000.0,
            holdingQuantity = 200,
            averagePurchasePrice = 100.0,
            reverseModeElapsedDays = 1,
        )

        val next = LaorV4StrategyEngine.applyFills(
            config = config,
            state = state,
            fills = listOf(
                LaorV4StrategyFill(LaorV4StrategySide.SELL, 80.0, 20, LaorV4StrategyOrderTag.REVERSE_LOC_SELL),
                LaorV4StrategyFill(LaorV4StrategySide.BUY, 75.0, 10, LaorV4StrategyOrderTag.REVERSE_BUY),
            ),
            closePrice = 70.0,
        )

        assertEquals(LaorV4StrategyMode.REVERSE, next.mode)
        assertDouble(18.5, next.progressRound)
        assertDouble(10_850.0, next.availableCash)
        assertEquals(190, next.holdingQuantity)
        assertDouble(98.6842105263, next.averagePurchasePrice)
        assertDouble(-400.0, next.realizedProfitLoss)
        assertEquals(2, next.reverseModeElapsedDays)
    }

    private fun assertOrder(
        order: LaorV4StrategyOrder,
        side: LaorV4StrategySide,
        type: LaorV4StrategyOrderType,
        price: Double,
        quantity: Long,
        tag: LaorV4StrategyOrderTag,
    ) {
        assertEquals(side, order.side)
        assertEquals(type, order.type)
        assertDouble(price, order.price ?: error("price is null"))
        assertEquals(quantity, order.quantity)
        assertEquals(tag, order.tag)
    }

    private fun assertDouble(expected: Double, actual: Double) {
        assertEquals(expected, actual, 0.000001)
    }

    private data class CalculationCase(
        val symbol: LaorV4StrategySymbol,
        val totalSplitCount: Int,
        val progressRound: Double,
        val expected: Double,
    )
}

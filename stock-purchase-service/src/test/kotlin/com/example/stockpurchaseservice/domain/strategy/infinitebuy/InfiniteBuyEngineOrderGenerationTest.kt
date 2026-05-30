package com.example.stockpurchaseservice.domain.strategy.infinitebuy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InfiniteBuyEngineOrderGenerationTest {
    @Test
    fun `rejects first buy multiplier at or below one`() {
        assertFailsWith<IllegalArgumentException> {
            InfiniteBuyConfig(
                symbol = SymbolProfile.TQQQ,
                splits = 20,
                firstBuyMultiplier = 1.0,
            )
        }
    }

    @Test
    fun `calculates star percentage for supported symbols and splits`() {
        val cases = listOf(
            CalculationCase(SymbolProfile.TQQQ, 20, 0.0, 15.0),
            CalculationCase(SymbolProfile.TQQQ, 20, 10.0, 0.0),
            CalculationCase(SymbolProfile.TQQQ, 30, 7.5, 7.5),
            CalculationCase(SymbolProfile.TQQQ, 40, 20.0, 0.0),
            CalculationCase(SymbolProfile.SOXL, 20, 10.0, 0.0),
            CalculationCase(SymbolProfile.SOXL, 30, 7.5, 10.0),
            CalculationCase(SymbolProfile.SOXL, 40, 10.0, 10.0),
        )

        cases.forEach { case ->
            val config = InfiniteBuyConfig(symbol = case.symbol, splits = case.splits)
            val state = InfiniteBuyState(t = case.t, cash = 0.0)

            assertDouble(case.expected, InfiniteBuyEngine.starPercentage(config, state))
        }
    }

    @Test
    fun `calculates normal star price target price and one-buy budget`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            t = 5.0,
            cash = 1_500.0,
            shares = 100,
            avgPrice = 100.0,
        )

        assertDouble(7.5, InfiniteBuyEngine.starPercentage(config, state))
        assertDouble(107.5, InfiniteBuyEngine.starPrice(config, state))
        assertDouble(115.0, InfiniteBuyEngine.targetPrice(config, state))
        assertDouble(100.0, InfiniteBuyEngine.oneBuyBudget(config, state))
    }

    @Test
    fun `plans first buy only when no shares are held`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(cash = 2_240.0)
        val market = InfiniteBuyMarket(previousClose = 100.0)

        val orders = InfiniteBuyEngine.generateOrders(config, state, market)

        assertEquals(1, orders.size)
        assertOrder(orders[0], TradeSide.BUY, TradeOrderType.LOC, 112.0, 1, OrderTag.FIRST_BUY)
    }

    @Test
    fun `plans first-half star and average half buys with normal sells`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            t = 4.0,
            cash = 32_000.0,
            shares = 100,
            avgPrice = 100.0,
        )
        val market = InfiniteBuyMarket(previousClose = 90.0)

        val orders = InfiniteBuyEngine.generateOrders(config, state, market)

        assertEquals(4, orders.size)
        assertOrder(orders[0], TradeSide.BUY, TradeOrderType.LOC, 108.99, 9, OrderTag.STAR_HALF_BUY)
        assertOrder(orders[1], TradeSide.BUY, TradeOrderType.LOC, 100.0, 10, OrderTag.AVG_HALF_BUY)
        assertOrder(orders[2], TradeSide.SELL, TradeOrderType.LOC, 109.0, 25, OrderTag.QUARTER_SELL)
        assertOrder(orders[3], TradeSide.SELL, TradeOrderType.LIMIT, 115.0, 75, OrderTag.TARGET_SELL)
    }

    @Test
    fun `plans second-half full star buy with normal sells`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            t = 12.0,
            cash = 16_000.0,
            shares = 100,
            avgPrice = 100.0,
        )
        val market = InfiniteBuyMarket(previousClose = 90.0)

        val orders = InfiniteBuyEngine.generateOrders(config, state, market)

        assertEquals(3, orders.size)
        assertOrder(orders[0], TradeSide.BUY, TradeOrderType.LOC, 96.99, 20, OrderTag.STAR_FULL_BUY)
        assertOrder(orders[1], TradeSide.SELL, TradeOrderType.LOC, 97.0, 25, OrderTag.QUARTER_SELL)
        assertOrder(orders[2], TradeSide.SELL, TradeOrderType.LIMIT, 115.0, 75, OrderTag.TARGET_SELL)
    }

    @Test
    fun `plans first reverse day MOC sell after entering reverse`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            t = 19.0,
            cash = 2_000.0,
            shares = 190,
            avgPrice = 100.0,
        )
        val enteredReverse = InfiniteBuyEngine.applyFills(
            config = config,
            state = state,
            fills = listOf(ExecutedFill(TradeSide.BUY, 100.0, 10, OrderTag.STAR_FULL_BUY)),
            closePrice = 80.0,
        )

        assertEquals(TradeMode.REVERSE, enteredReverse.mode)
        assertDouble(20.0, enteredReverse.t)
        assertEquals(0, enteredReverse.reverseDays)

        val orders = InfiniteBuyEngine.generateOrders(
            config = config,
            state = enteredReverse,
            market = InfiniteBuyMarket(previousClose = 80.0),
        )

        assertEquals(1, orders.size)
        assertEquals(TradeSide.SELL, orders[0].side)
        assertEquals(TradeOrderType.MOC, orders[0].type)
        assertNull(orders[0].price)
        assertEquals(20, orders[0].quantity)
        assertEquals(OrderTag.REVERSE_MOC_SELL, orders[0].tag)
    }

    @Test
    fun `plans later reverse LOC sell and cash-quarter buy from five-day average`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            mode = TradeMode.REVERSE,
            t = 18.0,
            cash = 10_000.0,
            shares = 180,
            avgPrice = 100.0,
            reverseDays = 1,
        )
        val market = InfiniteBuyMarket(
            previousClose = 86.0,
            recentClosePrices = listOf(78.0, 80.0, 82.0, 84.0, 86.0),
        )

        val orders = InfiniteBuyEngine.generateOrders(config, state, market)

        assertEquals(2, orders.size)
        assertOrder(orders[0], TradeSide.SELL, TradeOrderType.LOC, 82.0, 18, OrderTag.REVERSE_LOC_SELL)
        assertOrder(orders[1], TradeSide.BUY, TradeOrderType.LOC, 81.99, 30, OrderTag.REVERSE_BUY)
    }

    private fun assertDouble(expected: Double, actual: Double) {
        assertEquals(expected, actual, 0.000001)
    }

    private fun assertOrder(
        order: PlannedOrder,
        side: TradeSide,
        type: TradeOrderType,
        price: Double,
        quantity: Long,
        tag: OrderTag,
    ) {
        assertEquals(side, order.side)
        assertEquals(type, order.type)
        assertDouble(price, order.price ?: error("price is null"))
        assertEquals(quantity, order.quantity)
        assertEquals(tag, order.tag)
    }

    private data class CalculationCase(
        val symbol: SymbolProfile,
        val splits: Int,
        val t: Double,
        val expected: Double,
    )
}

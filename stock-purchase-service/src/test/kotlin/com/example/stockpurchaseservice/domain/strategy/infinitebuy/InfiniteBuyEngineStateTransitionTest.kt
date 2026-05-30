package com.example.stockpurchaseservice.domain.strategy.infinitebuy

import kotlin.test.Test
import kotlin.test.assertEquals

class InfiniteBuyEngineStateTransitionTest {
    @Test
    fun `order generation does not mutate state`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            t = 4.0,
            cash = 32_000.0,
            shares = 100,
            avgPrice = 100.0,
        )
        val before = state.copy()

        InfiniteBuyEngine.generateOrders(
            config = config,
            state = state,
            market = InfiniteBuyMarket(previousClose = 90.0),
        )

        assertEquals(before, state)
    }

    @Test
    fun `buy fills update cash shares weighted average and T by tag size`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(cash = 3_000.0)

        val next = InfiniteBuyEngine.applyFills(
            config = config,
            state = state,
            fills = listOf(
                ExecutedFill(TradeSide.BUY, 100.0, 10, OrderTag.FIRST_BUY),
                ExecutedFill(TradeSide.BUY, 80.0, 5, OrderTag.STAR_FULL_BUY),
                ExecutedFill(TradeSide.BUY, 90.0, 2, OrderTag.STAR_HALF_BUY),
                ExecutedFill(TradeSide.BUY, 95.0, 2, OrderTag.AVG_HALF_BUY),
            ),
            closePrice = 90.0,
        )

        assertEquals(TradeMode.NORMAL, next.mode)
        assertDouble(3.0, next.t)
        assertDouble(1_230.0, next.cash)
        assertEquals(19, next.shares)
        assertDouble(93.1578947368, next.avgPrice)
    }

    @Test
    fun `quarter sell reduces T proportionally and realizes pnl`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            t = 4.0,
            cash = 1_000.0,
            shares = 100,
            avgPrice = 100.0,
        )

        val next = InfiniteBuyEngine.applyFills(
            config = config,
            state = state,
            fills = listOf(ExecutedFill(TradeSide.SELL, 110.0, 25, OrderTag.QUARTER_SELL)),
            closePrice = 105.0,
        )

        assertEquals(TradeMode.NORMAL, next.mode)
        assertDouble(3.0, next.t)
        assertDouble(3_750.0, next.cash)
        assertEquals(75, next.shares)
        assertDouble(100.0, next.avgPrice)
        assertDouble(250.0, next.realizedPnl)
    }

    @Test
    fun `full sell resets position and mode`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            mode = TradeMode.REVERSE,
            t = 18.0,
            cash = 0.0,
            shares = 75,
            avgPrice = 100.0,
            realizedPnl = 250.0,
            reverseDays = 3,
        )

        val next = InfiniteBuyEngine.applyFills(
            config = config,
            state = state,
            fills = listOf(ExecutedFill(TradeSide.SELL, 115.0, 75, OrderTag.TARGET_SELL)),
            closePrice = 115.0,
        )

        assertEquals(TradeMode.NORMAL, next.mode)
        assertDouble(0.0, next.t)
        assertDouble(8_625.0, next.cash)
        assertEquals(0, next.shares)
        assertDouble(0.0, next.avgPrice)
        assertDouble(1_375.0, next.realizedPnl)
        assertEquals(0, next.reverseDays)
    }

    @Test
    fun `sell fills are applied before same-day buy fills when updating T`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            t = 4.0,
            cash = 1_000.0,
            shares = 100,
            avgPrice = 100.0,
        )

        val next = InfiniteBuyEngine.applyFills(
            config = config,
            state = state,
            fills = listOf(
                ExecutedFill(TradeSide.BUY, 90.0, 10, OrderTag.STAR_HALF_BUY),
                ExecutedFill(TradeSide.SELL, 115.0, 100, OrderTag.TARGET_SELL),
            ),
            closePrice = 100.0,
        )

        assertEquals(TradeMode.NORMAL, next.mode)
        assertDouble(0.5, next.t)
        assertDouble(11_600.0, next.cash)
        assertEquals(10, next.shares)
        assertDouble(90.0, next.avgPrice)
        assertDouble(1_500.0, next.realizedPnl)
    }

    @Test
    fun `target sell followed by half buy keeps quarter T and adds buy increment`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            t = 4.0,
            cash = 1_000.0,
            shares = 100,
            avgPrice = 100.0,
        )

        val next = InfiniteBuyEngine.applyFills(
            config = config,
            state = state,
            fills = listOf(
                ExecutedFill(TradeSide.SELL, 115.0, 75, OrderTag.TARGET_SELL),
                ExecutedFill(TradeSide.BUY, 90.0, 10, OrderTag.STAR_HALF_BUY),
            ),
            closePrice = 100.0,
        )

        assertEquals(TradeMode.NORMAL, next.mode)
        assertDouble(1.5, next.t)
        assertDouble(8_725.0, next.cash)
        assertEquals(35, next.shares)
        assertDouble(97.1428571429, next.avgPrice)
        assertDouble(1_125.0, next.realizedPnl)
    }

    @Test
    fun `normal mode enters reverse when T exceeds the last split`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            t = 19.0,
            cash = 2_000.0,
            shares = 190,
            avgPrice = 100.0,
        )

        val next = InfiniteBuyEngine.applyFills(
            config = config,
            state = state,
            fills = listOf(ExecutedFill(TradeSide.BUY, 100.0, 10, OrderTag.STAR_FULL_BUY)),
            closePrice = 80.0,
        )

        assertEquals(TradeMode.REVERSE, next.mode)
        assertDouble(20.0, next.t)
        assertEquals(200, next.shares)
        assertEquals(0, next.reverseDays)
    }

    @Test
    fun `reverse buy and sell fills update T and keep reverse running below exit price`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            mode = TradeMode.REVERSE,
            t = 20.0,
            cash = 10_000.0,
            shares = 200,
            avgPrice = 100.0,
            reverseDays = 1,
        )

        val next = InfiniteBuyEngine.applyFills(
            config = config,
            state = state,
            fills = listOf(
                ExecutedFill(TradeSide.SELL, 80.0, 20, OrderTag.REVERSE_LOC_SELL),
                ExecutedFill(TradeSide.BUY, 75.0, 10, OrderTag.REVERSE_BUY),
            ),
            closePrice = 70.0,
        )

        assertEquals(TradeMode.REVERSE, next.mode)
        assertDouble(18.5, next.t)
        assertDouble(10_850.0, next.cash)
        assertEquals(190, next.shares)
        assertDouble(98.6842105263, next.avgPrice)
        assertDouble(-400.0, next.realizedPnl)
        assertEquals(2, next.reverseDays)
    }

    @Test
    fun `reverse mode exits when close price recovers above threshold`() {
        val config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20)
        val state = InfiniteBuyState(
            mode = TradeMode.REVERSE,
            t = 18.0,
            cash = 1_000.0,
            shares = 180,
            avgPrice = 100.0,
            reverseDays = 2,
        )

        val next = InfiniteBuyEngine.applyFills(
            config = config,
            state = state,
            fills = emptyList(),
            closePrice = 86.0,
        )

        assertEquals(TradeMode.NORMAL, next.mode)
        assertDouble(18.0, next.t)
        assertEquals(0, next.reverseDays)
    }

    private fun assertDouble(expected: Double, actual: Double) {
        assertEquals(expected, actual, 0.000001)
    }
}

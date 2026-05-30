package com.example.stockpurchaseservice.domain.strategy.infinitebuy

import com.example.stockpurchaseservice.domain.strategy.execution.OrderSide
import com.example.stockpurchaseservice.domain.strategy.execution.OrderType
import com.example.stockpurchaseservice.domain.strategy.execution.StrategyExecutionFill
import com.example.stockpurchaseservice.domain.strategy.execution.StrategyExecutionId
import com.example.stockpurchaseservice.domain.strategy.execution.StrategyExecutionType
import com.example.stockpurchaseservice.domain.strategy.execution.StrategyMarketSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class InfiniteBuyExecutionTest {

    @Test
    fun `execution generates generic order intents from infinite buy engine`() {
        val execution = InfiniteBuyExecution(
            id = StrategyExecutionId("infinite-buy-v4:TQQQ"),
            config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20),
            state = InfiniteBuyState(cash = 2_240.0),
        )

        val plan = execution.generateOrders(StrategyMarketSnapshot(previousClose = 100.0))

        assertEquals(StrategyExecutionType.INFINITE_BUY_V4, execution.type)
        assertEquals(execution, plan.execution)
        assertEquals(1, plan.orders.size)
        with(plan.orders.single()) {
            assertEquals(StrategyExecutionId("infinite-buy-v4:TQQQ"), executionId)
            assertEquals(OrderSide.BUY, side)
            assertEquals(OrderType.LOC, type)
            assertDouble(112.0, price ?: error("price is null"))
            assertEquals(1, quantity)
            assertEquals(OrderTag.FIRST_BUY.name, tag)
        }
    }

    @Test
    fun `execution moves into reverse before order generation when T is exhausted`() {
        val execution = InfiniteBuyExecution(
            id = StrategyExecutionId("infinite-buy-v4:TQQQ"),
            config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20),
            state = InfiniteBuyState(
                t = 20.0,
                cash = 2_000.0,
                shares = 200,
                avgPrice = 100.0,
            ),
        )

        val plan = execution.generateOrders(StrategyMarketSnapshot(previousClose = 80.0))
        val nextExecution = plan.execution as InfiniteBuyExecution

        assertEquals(TradeMode.REVERSE, nextExecution.state.mode)
        assertEquals(0, nextExecution.state.reverseDays)
        assertEquals(1, plan.orders.size)
        with(plan.orders.single()) {
            assertEquals(OrderSide.SELL, side)
            assertEquals(OrderType.MOC, type)
            assertEquals(null, price)
            assertEquals(20, quantity)
            assertEquals(OrderTag.REVERSE_MOC_SELL.name, tag)
        }
    }

    @Test
    fun `execution applies generic fills back into infinite buy state`() {
        val execution = InfiniteBuyExecution(
            id = StrategyExecutionId("infinite-buy-v4:TQQQ"),
            config = InfiniteBuyConfig(symbol = SymbolProfile.TQQQ, splits = 20),
            state = InfiniteBuyState(cash = 3_000.0),
        )

        val next = execution.applyFills(
            fills = listOf(
                StrategyExecutionFill(
                    side = OrderSide.BUY,
                    price = 100.0,
                    quantity = 10,
                    tag = OrderTag.FIRST_BUY.name,
                ),
            ),
            closePrice = 100.0,
        )

        assertDouble(1.0, next.state.t)
        assertDouble(2_000.0, next.state.cash)
        assertEquals(10, next.state.shares)
        assertDouble(100.0, next.state.avgPrice)
    }

    private fun assertDouble(expected: Double, actual: Double) {
        assertEquals(expected, actual, 0.000001)
    }
}

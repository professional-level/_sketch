package com.example.strategyexecutionservice.domain.strategy.laor

import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.execution.OrderType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionFill
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionId
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyMarketSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class LaorV4StrategyTest {

    @Test
    fun `strategy generates generic order intents from Laor engine`() {
        val strategy = LaorV4Strategy(
            id = StrategyExecutionId("laor-v4-strategy:TQQQ"),
            config = LaorV4StrategyConfig(symbol = LaorV4StrategySymbol.TQQQ, totalSplitCount = 20),
            state = LaorV4StrategyState(availableCash = 2_240.0),
        )

        val plan = strategy.generateOrders(StrategyMarketSnapshot(previousClose = 100.0))

        assertEquals(StrategyExecutionType.LAOR_V4_STRATEGY, strategy.type)
        assertEquals(strategy, plan.execution)
        assertEquals(1, plan.orders.size)
        with(plan.orders.single()) {
            assertEquals(StrategyExecutionId("laor-v4-strategy:TQQQ"), executionId)
            assertEquals(OrderSide.BUY, side)
            assertEquals(OrderType.LOC, type)
            assertDouble(112.0, price ?: error("price is null"))
            assertEquals(1, quantity)
            assertEquals(LaorV4StrategyOrderTag.FIRST_BUY.name, tag)
        }
    }

    @Test
    fun `strategy moves into reverse before order generation when T is exhausted`() {
        val strategy = LaorV4Strategy(
            id = StrategyExecutionId("laor-v4-strategy:TQQQ"),
            config = LaorV4StrategyConfig(symbol = LaorV4StrategySymbol.TQQQ, totalSplitCount = 20),
            state = LaorV4StrategyState(
                progressRound = 20.0,
                availableCash = 2_000.0,
                holdingQuantity = 200,
                averagePurchasePrice = 100.0,
            ),
        )

        val plan = strategy.generateOrders(StrategyMarketSnapshot(previousClose = 80.0))
        val nextStrategy = plan.execution as LaorV4Strategy

        assertEquals(LaorV4StrategyMode.REVERSE, nextStrategy.state.mode)
        assertEquals(0, nextStrategy.state.reverseModeElapsedDays)
        assertEquals(1, plan.orders.size)
        with(plan.orders.single()) {
            assertEquals(OrderSide.SELL, side)
            assertEquals(OrderType.MOC, type)
            assertEquals(null, price)
            assertEquals(20, quantity)
            assertEquals(LaorV4StrategyOrderTag.REVERSE_MOC_SELL.name, tag)
        }
    }

    @Test
    fun `strategy applies generic fills back into Laor state`() {
        val strategy = LaorV4Strategy(
            id = StrategyExecutionId("laor-v4-strategy:TQQQ"),
            config = LaorV4StrategyConfig(symbol = LaorV4StrategySymbol.TQQQ, totalSplitCount = 20),
            state = LaorV4StrategyState(availableCash = 3_000.0),
        )

        val next = strategy.applyFills(
            fills = listOf(
                StrategyExecutionFill(
                    side = OrderSide.BUY,
                    price = 100.0,
                    quantity = 10,
                    tag = LaorV4StrategyOrderTag.FIRST_BUY.name,
                ),
            ),
            closePrice = 100.0,
        )

        assertDouble(1.0, next.state.progressRound)
        assertDouble(2_000.0, next.state.availableCash)
        assertEquals(10, next.state.holdingQuantity)
        assertDouble(100.0, next.state.averagePurchasePrice)
    }

    private fun assertDouble(expected: Double, actual: Double) {
        assertEquals(expected, actual, 0.000001)
    }
}

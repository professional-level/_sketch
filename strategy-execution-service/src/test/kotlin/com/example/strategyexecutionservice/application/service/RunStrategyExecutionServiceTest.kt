package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.`in`.LaorV4State
import com.example.strategyexecutionservice.application.port.`in`.MarketSnapshot
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.out.OrderIntentMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentPort
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.execution.OrderType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RunStrategyExecutionServiceTest {

    @Test
    fun `runs laor strategy and publishes order intent messages`() = runBlocking {
        val orderIntentPort = FakeOrderIntentPort()
        val service = RunStrategyExecutionService(orderIntentPort)

        val result = service.execute(
            RunStrategyExecutionCommand.LaorV4(
                executionId = "laor-v4-strategy:TQQQ",
                executionRunId = "2026-05-30",
                symbol = LaorV4StrategySymbol.TQQQ,
                totalSplitCount = 20,
                firstBuyLimitMultiplier = 1.12,
                state = LaorV4State(availableCash = 2_240.0),
                market = MarketSnapshot(previousClose = 100.0),
            ),
        )

        assertEquals("laor-v4-strategy:TQQQ", result.executionId)
        assertEquals("2026-05-30", result.executionRunId)
        assertEquals(1, result.createdOrderIntentCount)
        assertEquals(1, orderIntentPort.published.size)

        with(orderIntentPort.published.single()) {
            assertEquals("laor-v4-strategy:TQQQ", strategyExecutionId)
            assertEquals(StrategyExecutionType.LAOR_V4_STRATEGY, strategyType)
            assertEquals("TQQQ", symbol)
            assertEquals(OrderSide.BUY, side)
            assertEquals(OrderType.LOC, orderType)
            assertEquals(112.0, price ?: 0.0, 0.000001)
            assertEquals(1, quantity)
            assertEquals("FIRST_BUY", orderTag)
            assertEquals("laor-v4-strategy:TQQQ:2026-05-30:FIRST_BUY:0", idempotencyKey)
        }
    }

    private class FakeOrderIntentPort : OrderIntentPort {
        val published: MutableList<OrderIntentMessage> = mutableListOf()

        override suspend fun publishAll(orderIntents: List<OrderIntentMessage>) {
            published += orderIntents
        }
    }
}

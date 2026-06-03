package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillCommand
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillStatus
import com.example.strategyexecutionservice.application.port.`in`.OrderFillKind
import com.example.strategyexecutionservice.application.port.out.FinalPriceBatingV1ExecutionState
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.MarketDataPort
import com.example.strategyexecutionservice.application.port.out.OrderIntentMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentPort
import com.example.strategyexecutionservice.application.port.out.OrderTradingEnvironment
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventRecord
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventType
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import com.example.strategyexecutionservice.application.port.out.StrategyMarketDataSnapshot
import com.example.strategyexecutionservice.config.orderintent.OrderIntentProperties
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.execution.OrderType
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplyOrderFillServiceTest {

    @Test
    fun `applies laor fill and completes strategy when auto restart is disabled`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            initial = LaorV4ExecutionState(
                executionId = "laor-v4:TQQQ",
                symbol = LaorV4StrategySymbol.TQQQ,
                totalSplitCount = 20,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
                autoRestart = false,
                state = LaorV4StrategyState(
                    availableCash = 0.0,
                    holdingQuantity = 2,
                    averagePurchasePrice = 100.0,
                    progressRound = 1.0,
                ),
            ),
        )
        val orderEventPort = FakeStrategyExecutionOrderEventPort()
        val service = ApplyOrderFillService(
            statePort,
            FakeMarketDataPort(),
            orderEventPort,
            FakeOrderIntentPort(),
        )

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "fill-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.SELL,
                filledPrice = 120.0,
                filledQuantity = 2,
                orderTag = "TARGET_SELL",
                filledAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.APPLIED, result.status)
        assertEquals(StrategyExecutionOrderEventType.FILLED, orderEventPort.saved.single().type)
        with(statePort.saved.single()) {
            assertEquals(StrategyExecutionLifecycleStatus.COMPLETED, status)
            assertEquals(1, cycleNo)
            assertEquals(0L, state.holdingQuantity)
            assertEquals(240.0, state.availableCash)
        }
    }

    @Test
    fun `cycle close increments cycle number when auto restart is enabled`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            initial = LaorV4ExecutionState(
                executionId = "laor-v4:TQQQ",
                symbol = LaorV4StrategySymbol.TQQQ,
                totalSplitCount = 20,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
                autoRestart = true,
                cycleNo = 2,
                state = LaorV4StrategyState(
                    availableCash = 0.0,
                    holdingQuantity = 1,
                    averagePurchasePrice = 100.0,
                    progressRound = 1.0,
                ),
            ),
        )
        val service = ApplyOrderFillService(
            statePort,
            FakeMarketDataPort(),
            FakeStrategyExecutionOrderEventPort(),
            FakeOrderIntentPort(),
        )

        service.execute(
            ApplyOrderFillCommand(
                eventId = "fill-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.SELL,
                filledPrice = 120.0,
                filledQuantity = 1,
                orderTag = "TARGET_SELL",
                filledAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        with(statePort.saved.single()) {
            assertEquals(StrategyExecutionLifecycleStatus.ACTIVE, status)
            assertEquals(3, cycleNo)
        }
    }

    @Test
    fun `skips duplicate fill event without mutating strategy state`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            initial = LaorV4ExecutionState(
                executionId = "laor-v4:TQQQ",
                symbol = LaorV4StrategySymbol.TQQQ,
                totalSplitCount = 20,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
                state = LaorV4StrategyState(
                    availableCash = 1000.0,
                    holdingQuantity = 1,
                    averagePurchasePrice = 100.0,
                    progressRound = 1.0,
                ),
            ),
        )
        val service = ApplyOrderFillService(
            statePort,
            FakeMarketDataPort(),
            FakeStrategyExecutionOrderEventPort(duplicateEventIds = setOf("fill-1")),
            FakeOrderIntentPort(),
        )

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "fill-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.SELL,
                filledPrice = 120.0,
                filledQuantity = 1,
                orderTag = "TARGET_SELL",
                filledAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.SKIPPED_DUPLICATE, result.status)
        assertEquals(emptyList(), statePort.saved)
    }

    @Test
    fun `partial fill updates holdings without advancing laor progress round`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            initial = LaorV4ExecutionState(
                executionId = "laor-v4:TQQQ",
                symbol = LaorV4StrategySymbol.TQQQ,
                totalSplitCount = 20,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
                state = LaorV4StrategyState(
                    availableCash = 2_000.0,
                    holdingQuantity = 10,
                    averagePurchasePrice = 100.0,
                    progressRound = 4.0,
                ),
            ),
        )
        val orderEventPort = FakeStrategyExecutionOrderEventPort()
        val service = ApplyOrderFillService(
            statePort,
            FakeMarketDataPort(),
            orderEventPort,
            FakeOrderIntentPort(),
        )

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "partial-fill-1",
                strategyExecutionId = "laor-v4:TQQQ",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.BUY,
                fillKind = OrderFillKind.PARTIALLY_FILLED,
                filledPrice = 90.0,
                filledQuantity = 5,
                orderTag = "STAR_HALF_BUY",
                filledAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.APPLIED, result.status)
        assertEquals(StrategyExecutionOrderEventType.PARTIALLY_FILLED, orderEventPort.saved.single().type)
        with(statePort.saved.single().state) {
            assertEquals(4.0, progressRound)
            assertEquals(15, holdingQuantity)
            assertEquals(1_550.0, availableCash)
        }
    }

    @Test
    fun `final price bating partial fill records active filled quantity`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            finalPriceInitial = finalPriceState(quantity = 3),
        )
        val orderEventPort = FakeStrategyExecutionOrderEventPort()
        val service = ApplyOrderFillService(
            statePort,
            FakeMarketDataPort(),
            orderEventPort,
            FakeOrderIntentPort(),
        )

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "final-price-partial-1",
                strategyExecutionId = "FinalPriceBatingV1:005930",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.BUY,
                fillKind = OrderFillKind.PARTIALLY_FILLED,
                filledPrice = 70_000.0,
                filledQuantity = 1,
                orderTag = "ENTRY_BUY",
                filledAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.APPLIED, result.status)
        assertEquals(StrategyExecutionOrderEventType.PARTIALLY_FILLED, orderEventPort.saved.single().type)
        with(statePort.finalPriceSaved.single()) {
            assertEquals(StrategyExecutionLifecycleStatus.ACTIVE, status)
            assertEquals(1L, filledQuantity)
            assertEquals(70_000.0, averageFilledPrice)
            assertEquals(null, completedAt)
        }
    }

    @Test
    fun `final price bating entry full fill creates sell intent`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            finalPriceInitial = finalPriceState(quantity = 2, filledQuantity = 1, averageFilledPrice = 70_000.0),
        )
        val orderEventPort = FakeStrategyExecutionOrderEventPort()
        val orderIntentPort = FakeOrderIntentPort()
        val service = ApplyOrderFillService(
            statePort,
            FakeMarketDataPort(),
            orderEventPort,
            orderIntentPort,
            tradingEnvironmentResolver = OrderIntentTradingEnvironmentResolver(
                OrderIntentProperties().apply {
                    strategyTradingEnvironments["FinalPriceBatingV1"] = OrderTradingEnvironment.LIVE
                },
            ),
        )

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "final-price-filled-1",
                strategyExecutionId = "FinalPriceBatingV1:005930",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.BUY,
                fillKind = OrderFillKind.FILLED,
                filledPrice = 72_000.0,
                filledQuantity = 1,
                orderTag = "ENTRY_BUY",
                filledAt = ZonedDateTime.parse("2026-06-02T09:30:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.APPLIED, result.status)
        assertEquals(StrategyExecutionOrderEventType.FILLED, orderEventPort.saved.single().type)
        with(statePort.finalPriceSaved.single()) {
            assertEquals(StrategyExecutionLifecycleStatus.ACTIVE, status)
            assertEquals(2L, filledQuantity)
            assertEquals(71_000.0, averageFilledPrice)
            assertEquals(73_130.0, sellTargetPrice)
            assertEquals(2L, sellQuantity)
            assertEquals(ZonedDateTime.parse("2026-06-02T09:30:00+09:00"), sellIntentCreatedAt)
            assertEquals(null, completedAt)
        }
        with(orderIntentPort.published.single()) {
            assertEquals("FinalPriceBatingV1:005930", strategyExecutionId)
            assertEquals(OrderSide.SELL, side)
            assertEquals(OrderType.LIMIT, orderType)
            assertEquals(73_130.0, price)
            assertEquals(2L, quantity)
            assertEquals("FINAL_PRICE_BATING_V1_SELL", orderTag)
            assertEquals(OrderTradingEnvironment.LIVE, tradingEnvironment)
        }
    }

    @Test
    fun `final price bating overflow entry fill caps quantity and creates one sell intent`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            finalPriceInitial = finalPriceState(quantity = 2, filledQuantity = 1, averageFilledPrice = 70_000.0),
        )
        val orderIntentPort = FakeOrderIntentPort()
        val service = ApplyOrderFillService(
            statePort,
            FakeMarketDataPort(),
            FakeStrategyExecutionOrderEventPort(),
            orderIntentPort,
        )

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "final-price-overflow-1",
                strategyExecutionId = "FinalPriceBatingV1:005930",
                orderIntentId = "intent-1",
                brokerOrderId = "broker-1",
                side = OrderSide.BUY,
                fillKind = OrderFillKind.PARTIALLY_FILLED,
                filledPrice = 72_000.0,
                filledQuantity = 2,
                orderTag = "ENTRY_BUY",
                filledAt = ZonedDateTime.parse("2026-06-02T09:35:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.APPLIED, result.status)
        with(statePort.finalPriceSaved.single()) {
            assertEquals(StrategyExecutionLifecycleStatus.ACTIVE, status)
            assertEquals(2L, filledQuantity)
            assertEquals(71_000.0, averageFilledPrice)
            assertEquals(73_130.0, sellTargetPrice)
            assertEquals(2L, sellQuantity)
            assertEquals(ZonedDateTime.parse("2026-06-02T09:35:00+09:00"), sellIntentCreatedAt)
            assertEquals(null, completedAt)
        }
        assertEquals(1, orderIntentPort.published.size)
        assertEquals(2L, orderIntentPort.published.single().quantity)
    }

    @Test
    fun `final price bating exit sell fill completes strategy`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(
            finalPriceInitial = finalPriceState(
                quantity = 2,
                filledQuantity = 2,
                averageFilledPrice = 71_000.0,
                sellTargetPrice = 73_130.0,
                sellQuantity = 2,
                sellIntentCreatedAt = ZonedDateTime.parse("2026-06-02T09:30:00+09:00"),
            ),
        )
        val orderIntentPort = FakeOrderIntentPort()
        val service = ApplyOrderFillService(
            statePort,
            FakeMarketDataPort(),
            FakeStrategyExecutionOrderEventPort(),
            orderIntentPort,
        )

        val result = service.execute(
            ApplyOrderFillCommand(
                eventId = "final-price-sell-filled-1",
                strategyExecutionId = "FinalPriceBatingV1:005930",
                orderIntentId = "intent-sell-1",
                brokerOrderId = "broker-sell-1",
                side = OrderSide.SELL,
                fillKind = OrderFillKind.FILLED,
                filledPrice = 73_500.0,
                filledQuantity = 2,
                orderTag = "FINAL_PRICE_BATING_V1_SELL",
                filledAt = ZonedDateTime.parse("2026-06-02T10:30:00+09:00"),
            ),
        )

        assertEquals(ApplyOrderFillStatus.APPLIED, result.status)
        with(statePort.finalPriceSaved.single()) {
            assertEquals(StrategyExecutionLifecycleStatus.COMPLETED, status)
            assertEquals(2L, soldQuantity)
            assertEquals(73_500.0, averageSoldPrice)
            assertEquals(ZonedDateTime.parse("2026-06-02T10:30:00+09:00"), completedAt)
        }
        assertEquals(emptyList(), orderIntentPort.published)
    }

    private class FakeStrategyExecutionStatePort(
        private val initial: LaorV4ExecutionState? = null,
        private val finalPriceInitial: FinalPriceBatingV1ExecutionState? = null,
    ) : StrategyExecutionStatePort {
        val saved: MutableList<LaorV4ExecutionState> = mutableListOf()
        val finalPriceSaved: MutableList<FinalPriceBatingV1ExecutionState> = mutableListOf()

        override suspend fun tryMarkStartRequested(idempotencyKey: String): Boolean = true

        override suspend fun findLaorV4Strategy(executionId: String): LaorV4ExecutionState? {
            return initial?.takeIf { it.executionId == executionId }
        }

        override suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState> {
            return listOfNotNull(initial).filter { it.status == StrategyExecutionLifecycleStatus.ACTIVE }
        }

        override suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState) {
            saved += state
        }

        override suspend fun findFinalPriceBatingV1Strategy(executionId: String): FinalPriceBatingV1ExecutionState? {
            return finalPriceSaved.lastOrNull { it.executionId == executionId }
                ?: finalPriceInitial?.takeIf { it.executionId == executionId }
        }

        override suspend fun saveFinalPriceBatingV1Strategy(state: FinalPriceBatingV1ExecutionState) {
            finalPriceSaved += state
        }
    }

    private fun finalPriceState(
        quantity: Long,
        filledQuantity: Long = 0,
        averageFilledPrice: Double? = null,
        sellTargetPrice: Double? = null,
        sellQuantity: Long = 0,
        sellIntentCreatedAt: ZonedDateTime? = null,
        soldQuantity: Long = 0,
        averageSoldPrice: Double? = null,
    ): FinalPriceBatingV1ExecutionState {
        return FinalPriceBatingV1ExecutionState(
            executionId = "FinalPriceBatingV1:005930",
            symbol = "005930",
            market = "KRX",
            budget = 140_000.0,
            targetBuyPrice = 70_000.0,
            quantity = quantity,
            filledQuantity = filledQuantity,
            averageFilledPrice = averageFilledPrice,
            sellTargetPrice = sellTargetPrice,
            sellQuantity = sellQuantity,
            sellIntentCreatedAt = sellIntentCreatedAt,
            soldQuantity = soldQuantity,
            averageSoldPrice = averageSoldPrice,
            startedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
        )
    }

    private class FakeMarketDataPort : MarketDataPort {
        override suspend fun getMarketSnapshot(
            symbol: String,
            recentCloseCount: Int,
        ): StrategyMarketDataSnapshot {
            return StrategyMarketDataSnapshot(
                previousClose = 120.0,
                recentClosePrices = listOf(120.0, 119.0, 118.0, 117.0, 116.0),
            )
        }
    }

    private class FakeStrategyExecutionOrderEventPort(
        private val duplicateEventIds: Set<String> = emptySet(),
    ) : StrategyExecutionOrderEventPort {
        val saved: MutableList<StrategyExecutionOrderEventRecord> = mutableListOf()

        override suspend fun tryRecord(event: StrategyExecutionOrderEventRecord): Boolean {
            if (event.eventId in duplicateEventIds) return false

            saved += event
            return true
        }
    }

    private class FakeOrderIntentPort : OrderIntentPort {
        val published: MutableList<OrderIntentMessage> = mutableListOf()

        override suspend fun publishAll(orderIntents: List<OrderIntentMessage>) {
            published += orderIntents
        }
    }
}

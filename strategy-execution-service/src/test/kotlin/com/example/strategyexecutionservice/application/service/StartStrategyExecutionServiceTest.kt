package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.`in`.LaorV4State
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionStatus
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionStatus
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.MarketDataPort
import com.example.strategyexecutionservice.application.port.out.OrderIntentMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentPort
import com.example.strategyexecutionservice.application.port.out.OrderTradingEnvironment
import com.example.strategyexecutionservice.application.port.out.FinalPriceBatingV1ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import com.example.strategyexecutionservice.application.port.out.StrategyMarketDataSnapshot
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.execution.OrderType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class StartStrategyExecutionServiceTest {

    @Test
    fun `starts final price bating by publishing single buy order intent`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort()
        val orderIntentPort = FakeOrderIntentPort()
        val service = StartStrategyExecutionService(
            strategyExecutionStatePort = statePort,
            registerLaorV4StrategyExecutionUseCase = FakeRegisterLaorUseCase(),
            runStrategyExecutionUseCase = FakeRunStrategyExecutionUseCase(),
            marketDataPort = FakeMarketDataPort(),
            orderIntentPort = orderIntentPort,
        )

        val result = service.execute(
            StartStrategyExecutionCommand.FinalPriceBatingV1(
                executionId = "FinalPriceBatingV1:005930",
                idempotencyKey = "FINAL_PRICE_BATING_V1:005930:2026-06-02",
                strategyVersion = "v1",
                symbol = "005930",
                market = "KRX",
                budget = 140_000.0,
                targetBuyPrice = 70_000.0,
                quantityPolicy = "BUDGET_DIVIDED_BY_TARGET_BUY_PRICE",
                requestedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(StartStrategyExecutionStatus.STARTED, result.status)
        assertEquals(1, result.createdOrderIntentCount)
        with(orderIntentPort.published.single()) {
            assertEquals("FinalPriceBatingV1:005930", strategyExecutionId)
            assertEquals(StrategyExecutionType.FINAL_PRICE_BATING_V1_STRATEGY, strategyType)
            assertEquals("005930", symbol)
            assertEquals(OrderSide.BUY, side)
            assertEquals(OrderType.LIMIT, orderType)
            assertEquals(70_000.0, price)
            assertEquals(2, quantity)
            assertEquals("ENTRY_BUY", orderTag)
            assertEquals(OrderTradingEnvironment.MOCK, tradingEnvironment)
        }
        with(statePort.finalPriceStates.single()) {
            assertEquals("FinalPriceBatingV1:005930", executionId)
            assertEquals("005930", symbol)
            assertEquals("KRX", market)
            assertEquals(140_000.0, budget)
            assertEquals(70_000.0, targetBuyPrice)
            assertEquals(2, quantity)
            assertEquals(0, filledQuantity)
        }
    }

    @Test
    fun `starts final price bating without order when budget cannot buy one share`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort()
        val orderIntentPort = FakeOrderIntentPort()
        val service = StartStrategyExecutionService(
            strategyExecutionStatePort = statePort,
            registerLaorV4StrategyExecutionUseCase = FakeRegisterLaorUseCase(),
            runStrategyExecutionUseCase = FakeRunStrategyExecutionUseCase(),
            marketDataPort = FakeMarketDataPort(),
            orderIntentPort = orderIntentPort,
        )

        val result = service.execute(
            StartStrategyExecutionCommand.FinalPriceBatingV1(
                executionId = "FinalPriceBatingV1:005930",
                idempotencyKey = "FINAL_PRICE_BATING_V1:005930:2026-06-02",
                strategyVersion = "v1",
                symbol = "005930",
                market = "KRX",
                budget = 10_000.0,
                targetBuyPrice = 70_000.0,
                quantityPolicy = "BUDGET_DIVIDED_BY_TARGET_BUY_PRICE",
                requestedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(StartStrategyExecutionStatus.STARTED, result.status)
        assertEquals(0, result.createdOrderIntentCount)
        assertEquals(emptyList(), orderIntentPort.published)
        with(statePort.finalPriceStates.single()) {
            assertEquals(0, quantity)
            assertEquals(0, filledQuantity)
            assertEquals(com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus.COMPLETED, status)
            assertEquals(ZonedDateTime.parse("2026-06-02T09:00:00+09:00"), completedAt)
        }
    }

    @Test
    fun `skips duplicate start request by idempotency key`() = runBlocking {
        val statePort = FakeStrategyExecutionStatePort(startResult = false)
        val orderIntentPort = FakeOrderIntentPort()
        val service = StartStrategyExecutionService(
            strategyExecutionStatePort = statePort,
            registerLaorV4StrategyExecutionUseCase = FakeRegisterLaorUseCase(),
            runStrategyExecutionUseCase = FakeRunStrategyExecutionUseCase(),
            marketDataPort = FakeMarketDataPort(),
            orderIntentPort = orderIntentPort,
        )

        val result = service.execute(
            StartStrategyExecutionCommand.FinalPriceBatingV1(
                executionId = "FinalPriceBatingV1:005930",
                idempotencyKey = "duplicate",
                strategyVersion = "v1",
                symbol = "005930",
                market = "KRX",
                budget = 140_000.0,
                targetBuyPrice = 70_000.0,
                quantityPolicy = "BUDGET_DIVIDED_BY_TARGET_BUY_PRICE",
                requestedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(StartStrategyExecutionStatus.SKIPPED_DUPLICATE, result.status)
        assertEquals(emptyList(), orderIntentPort.published)
    }

    private class FakeStrategyExecutionStatePort(
        private val startResult: Boolean = true,
    ) : StrategyExecutionStatePort {
        val finalPriceStates: MutableList<FinalPriceBatingV1ExecutionState> = mutableListOf()

        override suspend fun tryMarkStartRequested(idempotencyKey: String): Boolean = startResult
        override suspend fun findLaorV4Strategy(executionId: String): LaorV4ExecutionState? = null
        override suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState> = emptyList()
        override suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState) = Unit
        override suspend fun findFinalPriceBatingV1Strategy(executionId: String): FinalPriceBatingV1ExecutionState? {
            return finalPriceStates.lastOrNull { it.executionId == executionId }
        }

        override suspend fun saveFinalPriceBatingV1Strategy(state: FinalPriceBatingV1ExecutionState) {
            finalPriceStates += state
        }
    }

    private class FakeRegisterLaorUseCase : RegisterLaorV4StrategyExecutionUseCase {
        override suspend fun execute(
            command: RegisterLaorV4StrategyExecutionCommand,
        ): RegisterLaorV4StrategyExecutionResult {
            return RegisterLaorV4StrategyExecutionResult(
                executionId = command.executionId,
                status = RegisterLaorV4StrategyExecutionStatus.REGISTERED,
                symbol = command.symbol,
                budget = command.budget,
                totalSplitCount = command.totalSplitCount,
                firstBuyLimitPercentAbovePreviousClose = command.firstBuyLimitPercentAbovePreviousClose,
                autoRestart = command.autoRestart,
            )
        }
    }

    private class FakeRunStrategyExecutionUseCase : RunStrategyExecutionUseCase {
        override suspend fun execute(command: RunStrategyExecutionCommand): RunStrategyExecutionResult {
            return RunStrategyExecutionResult(
                executionId = command.executionId,
                executionRunId = command.executionRunId,
                createdOrderIntentCount = 1,
                plannedState = LaorV4State(availableCash = 1_000.0),
            )
        }
    }

    private class FakeMarketDataPort : MarketDataPort {
        override suspend fun getMarketSnapshot(
            symbol: String,
            asOfDate: java.time.LocalDate,
            recentCloseCount: Int,
        ): StrategyMarketDataSnapshot {
            return StrategyMarketDataSnapshot(
                previousClose = 100.0,
                recentClosePrices = listOf(100.0, 99.0, 98.0, 97.0, 96.0),
            )
        }
    }

    private class FakeOrderIntentPort : OrderIntentPort {
        val published: MutableList<OrderIntentMessage> = mutableListOf()

        override suspend fun publishAll(orderIntents: List<OrderIntentMessage>) {
            published += orderIntents
        }
    }
}

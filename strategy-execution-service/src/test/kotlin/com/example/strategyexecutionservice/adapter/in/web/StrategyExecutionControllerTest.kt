package com.example.strategyexecutionservice.adapter.`in`.web

import com.example.strategyexecutionservice.application.port.`in`.FinalPriceBatingV1StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.LaorV4StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.QueryFinalPriceBatingV1StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.`in`.QueryLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionStatus
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyExecutionControllerTest {

    @Test
    fun `registers laor v4 request with generated execution id`() = runBlocking {
        val useCase = FakeRegisterLaorV4StrategyExecutionUseCase()
        val controller = controller(registerUseCase = useCase)

        val response = controller.registerLaorV4StrategyExecution(
            RegisterLaorV4StrategyExecutionRequest(
                symbol = "tqqq",
                budget = 10_000.0,
                totalSplitCount = 20,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
            ),
        )

        assertEquals("laor-v4:TQQQ", useCase.commands.single().executionId)
        assertEquals(LaorV4StrategySymbol.TQQQ, useCase.commands.single().symbol)
        assertEquals("REGISTERED", response.status)
        assertEquals("TQQQ", response.symbol)
    }

    @Test
    fun `gets laor v4 strategy execution state for operations`() = runBlocking {
        val execution = view(
            executionId = "laor-v4:TQQQ",
            progressRound = 3.5,
            availableCash = 1_200.0,
            holdingQuantity = 7,
            averagePurchasePrice = 101.2,
        )
        val controller = StrategyExecutionController(
            FakeRegisterLaorV4StrategyExecutionUseCase(),
            FakeQueryLaorV4StrategyExecutionUseCase(states = listOf(execution)),
            FakeQueryFinalPriceBatingV1StrategyExecutionUseCase(),
        )

        val response = controller.getLaorV4StrategyExecution("laor-v4:TQQQ")

        assertEquals(200, response.statusCode.value())
        with(checkNotNull(response.body)) {
            assertEquals("laor-v4:TQQQ", executionId)
            assertEquals("TQQQ", symbol)
            assertEquals("ACTIVE", status)
            assertEquals(3.5, progressRound)
            assertEquals(1_200.0, availableCash)
            assertEquals(7, holdingQuantity)
            assertEquals(101.2, averagePurchasePrice)
        }
    }

    @Test
    fun `lists active laor v4 strategy execution states`() = runBlocking {
        val controller = StrategyExecutionController(
            FakeRegisterLaorV4StrategyExecutionUseCase(),
            FakeQueryLaorV4StrategyExecutionUseCase(
                states = listOf(
                    view(executionId = "laor-v4:TQQQ"),
                    view(executionId = "laor-v4:SOXL", symbol = LaorV4StrategySymbol.SOXL),
                ),
            ),
            FakeQueryFinalPriceBatingV1StrategyExecutionUseCase(),
        )

        val response = controller.getActiveLaorV4StrategyExecutions()

        assertEquals(listOf("laor-v4:TQQQ", "laor-v4:SOXL"), response.map { it.executionId })
    }

    @Test
    fun `gets final price bating strategy execution state for operations`() = runBlocking {
        val execution = finalPriceView(
            filledQuantity = 4,
            averageFilledPrice = 95.0,
            soldQuantity = 1,
            averageSoldPrice = 100.0,
        )
        val controller = controller(
            finalPriceQueryUseCase = FakeQueryFinalPriceBatingV1StrategyExecutionUseCase(states = listOf(execution)),
        )

        val response = controller.getFinalPriceBatingV1StrategyExecution("FinalPriceBatingV1:005930")

        assertEquals(200, response.statusCode.value())
        with(checkNotNull(response.body)) {
            assertEquals("FinalPriceBatingV1:005930", executionId)
            assertEquals("005930", symbol)
            assertEquals("KRX", market)
            assertEquals("ACTIVE", status)
            assertEquals(4L, filledQuantity)
            assertEquals(95.0, averageFilledPrice)
            assertEquals(6L, remainingBuyQuantity)
            assertEquals(3L, currentHoldingQuantity)
            assertEquals(720.0, currentCash)
            assertEquals(95.0, currentAveragePrice)
            assertEquals(1L, soldQuantity)
            assertEquals(9L, remainingSellQuantity)
        }
    }

    @Test
    fun `lists active final price bating strategy execution states`() = runBlocking {
        val controller = controller(
            finalPriceQueryUseCase = FakeQueryFinalPriceBatingV1StrategyExecutionUseCase(
                states = listOf(
                    finalPriceView(executionId = "FinalPriceBatingV1:005930"),
                    finalPriceView(executionId = "FinalPriceBatingV1:000660", symbol = "000660"),
                ),
            ),
        )

        val response = controller.getActiveFinalPriceBatingV1StrategyExecutions()

        assertEquals(listOf("FinalPriceBatingV1:005930", "FinalPriceBatingV1:000660"), response.map { it.executionId })
    }

    private fun controller(
        registerUseCase: RegisterLaorV4StrategyExecutionUseCase = FakeRegisterLaorV4StrategyExecutionUseCase(),
        laorQueryUseCase: QueryLaorV4StrategyExecutionUseCase = FakeQueryLaorV4StrategyExecutionUseCase(),
        finalPriceQueryUseCase: QueryFinalPriceBatingV1StrategyExecutionUseCase =
            FakeQueryFinalPriceBatingV1StrategyExecutionUseCase(),
    ): StrategyExecutionController {
        return StrategyExecutionController(
            registerUseCase,
            laorQueryUseCase,
            finalPriceQueryUseCase,
        )
    }

    private class FakeRegisterLaorV4StrategyExecutionUseCase : RegisterLaorV4StrategyExecutionUseCase {
        val commands: MutableList<RegisterLaorV4StrategyExecutionCommand> = mutableListOf()

        override suspend fun execute(
            command: RegisterLaorV4StrategyExecutionCommand,
        ): RegisterLaorV4StrategyExecutionResult {
            commands += command
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

    private class FakeQueryLaorV4StrategyExecutionUseCase(
        private val states: List<LaorV4StrategyExecutionView> = emptyList(),
    ) : QueryLaorV4StrategyExecutionUseCase {
        override suspend fun find(executionId: String): LaorV4StrategyExecutionView? {
            return states.firstOrNull { it.executionId == executionId }
        }

        override suspend fun findActive(): List<LaorV4StrategyExecutionView> {
            return states
        }
    }

    private class FakeQueryFinalPriceBatingV1StrategyExecutionUseCase(
        private val states: List<FinalPriceBatingV1StrategyExecutionView> = emptyList(),
    ) : QueryFinalPriceBatingV1StrategyExecutionUseCase {
        override suspend fun find(executionId: String): FinalPriceBatingV1StrategyExecutionView? {
            return states.firstOrNull { it.executionId == executionId }
        }

        override suspend fun findActive(): List<FinalPriceBatingV1StrategyExecutionView> {
            return states
        }
    }

    private fun view(
        executionId: String,
        symbol: LaorV4StrategySymbol = LaorV4StrategySymbol.TQQQ,
        progressRound: Double = 1.0,
        availableCash: Double = 10_000.0,
        holdingQuantity: Long = 0,
        averagePurchasePrice: Double = 0.0,
    ): LaorV4StrategyExecutionView {
        return LaorV4StrategyExecutionView(
            executionId = executionId,
            symbol = symbol,
            status = StrategyExecutionLifecycleStatus.ACTIVE,
            cycleNo = 1,
            totalSplitCount = 20,
            firstBuyLimitPercentAbovePreviousClose = 12.0,
            autoRestart = true,
            mode = LaorV4StrategyMode.NORMAL,
            progressRound = progressRound,
            availableCash = availableCash,
            holdingQuantity = holdingQuantity,
            averagePurchasePrice = averagePurchasePrice,
            realizedProfitLoss = 0.0,
            reverseModeElapsedDays = 0,
            lastExecutionRunId = "ACTIVE_STRATEGIES_DAILY:2026-06-02",
            lastExecutedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
        )
    }

    private fun finalPriceView(
        executionId: String = "FinalPriceBatingV1:005930",
        symbol: String = "005930",
        filledQuantity: Long = 0,
        averageFilledPrice: Double? = null,
        soldQuantity: Long = 0,
        averageSoldPrice: Double? = null,
    ): FinalPriceBatingV1StrategyExecutionView {
        return FinalPriceBatingV1StrategyExecutionView(
            executionId = executionId,
            symbol = symbol,
            market = "KRX",
            status = StrategyExecutionLifecycleStatus.ACTIVE,
            budget = 1_000.0,
            targetBuyPrice = 90.0,
            quantity = 10,
            filledQuantity = filledQuantity,
            averageFilledPrice = averageFilledPrice,
            remainingBuyQuantity = 10L - filledQuantity,
            sellTargetPrice = averageFilledPrice?.let { it * 1.03 },
            sellQuantity = 10,
            soldQuantity = soldQuantity,
            averageSoldPrice = averageSoldPrice,
            remainingSellQuantity = 10L - soldQuantity,
            currentCash = 1_000.0 - ((averageFilledPrice ?: 90.0) * filledQuantity) +
                ((averageSoldPrice ?: 0.0) * soldQuantity),
            currentHoldingQuantity = filledQuantity - soldQuantity,
            currentAveragePrice = averageFilledPrice,
            sellIntentCreatedAt = ZonedDateTime.parse("2026-06-02T09:01:00+09:00"),
            startedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            completedAt = null,
        )
    }
}

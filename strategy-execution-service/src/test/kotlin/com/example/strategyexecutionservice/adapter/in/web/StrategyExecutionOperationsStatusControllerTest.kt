package com.example.strategyexecutionservice.adapter.`in`.web

import com.example.strategyexecutionservice.application.port.`in`.FinalPriceBatingV1StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.GetStrategyExecutionOperationsStatusUseCase
import com.example.strategyexecutionservice.application.port.`in`.LaorV4StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.StrategyExecutionOperationsStatusResult
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusSnapshot
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatusCount
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyExecutionOperationsStatusControllerTest {

    @Test
    fun `returns strategy execution operations status response`() = runBlocking {
        val result = StrategyExecutionOperationsStatusResult(
            generatedAt = ZonedDateTime.parse("2026-06-02T10:00:00+09:00[Asia/Seoul]"),
            snapshot = snapshot(),
            activeLaorV4Strategies = listOf(laorView()),
            activeFinalPriceBatingV1Strategies = listOf(finalPriceView()),
        )
        val useCase = FakeGetStrategyExecutionOperationsStatusUseCase(result)
        val controller = StrategyExecutionOperationsStatusController(useCase)

        val response = controller.status()

        assertEquals(1, useCase.callCount)
        assertEquals(result.generatedAt, response.generatedAt)
        assertEquals(result.snapshot.orderIntentOutboxStatusCounts, response.orderIntentOutboxStatusCounts)
        assertEquals(7, response.strategyExecutionStartRequestCount)
        assertEquals(
            result.snapshot.strategyExecutionOrderEventTypeCounts,
            response.strategyExecutionOrderEventTypeCounts,
        )
        assertEquals(result.snapshot.laorV4StatusCounts, response.laorV4StatusCounts)
        assertEquals(result.snapshot.finalPriceBatingV1StatusCounts, response.finalPriceBatingV1StatusCounts)
        with(response.activeLaorV4Strategies.single()) {
            assertEquals("laor-v4:TQQQ", executionId)
            assertEquals("TQQQ", symbol)
            assertEquals("ACTIVE", status)
            assertEquals(4.5, progressRound)
            assertEquals(1_500.0, availableCash)
            assertEquals(3, holdingQuantity)
            assertEquals(100.0, averagePurchasePrice)
        }
        with(response.activeFinalPriceBatingV1Strategies.single()) {
            assertEquals("FinalPriceBatingV1:005930", executionId)
            assertEquals("005930", symbol)
            assertEquals("ACTIVE", status)
            assertEquals(796_000.0, currentCash)
            assertEquals(3, currentHoldingQuantity)
            assertEquals(69_000.0, currentAveragePrice)
        }
    }

    private fun snapshot(): StrategyExecutionOperationsStatusSnapshot {
        return StrategyExecutionOperationsStatusSnapshot(
            orderIntentOutboxStatusCounts = listOf(
                StrategyExecutionStatusCount("PENDING", 2),
                StrategyExecutionStatusCount("FAILED", 1),
            ),
            strategyExecutionStartRequestCount = 7,
            strategyExecutionOrderEventTypeCounts = listOf(
                StrategyExecutionStatusCount("SUBMITTED", 3),
                StrategyExecutionStatusCount("FILLED", 2),
            ),
            laorV4StatusCounts = listOf(
                StrategyExecutionStatusCount("ACTIVE", 1),
                StrategyExecutionStatusCount("COMPLETED", 4),
            ),
            finalPriceBatingV1StatusCounts = listOf(
                StrategyExecutionStatusCount("ACTIVE", 2),
                StrategyExecutionStatusCount("COMPLETED", 5),
            ),
        )
    }

    private class FakeGetStrategyExecutionOperationsStatusUseCase(
        private val result: StrategyExecutionOperationsStatusResult,
    ) : GetStrategyExecutionOperationsStatusUseCase {
        var callCount: Int = 0

        override suspend fun execute(): StrategyExecutionOperationsStatusResult {
            callCount += 1
            return result
        }
    }

    private fun laorView(): LaorV4StrategyExecutionView {
        return LaorV4StrategyExecutionView(
            executionId = "laor-v4:TQQQ",
            symbol = LaorV4StrategySymbol.TQQQ,
            status = StrategyExecutionLifecycleStatus.ACTIVE,
            cycleNo = 2,
            totalSplitCount = 20,
            firstBuyLimitMultiplier = 1.12,
            autoRestart = true,
            mode = LaorV4StrategyMode.NORMAL,
            progressRound = 4.5,
            availableCash = 1_500.0,
            holdingQuantity = 3,
            averagePurchasePrice = 100.0,
            realizedProfitLoss = 15.0,
            reverseModeElapsedDays = 0,
            lastExecutionRunId = "ACTIVE_STRATEGIES_DAILY:2026-06-02",
            lastExecutedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00[Asia/Seoul]"),
        )
    }

    private fun finalPriceView(): FinalPriceBatingV1StrategyExecutionView {
        return FinalPriceBatingV1StrategyExecutionView(
            executionId = "FinalPriceBatingV1:005930",
            symbol = "005930",
            market = "KRX",
            status = StrategyExecutionLifecycleStatus.ACTIVE,
            budget = 1_000_000.0,
            targetBuyPrice = 70_000.0,
            quantity = 10,
            filledQuantity = 4,
            averageFilledPrice = 69_000.0,
            remainingBuyQuantity = 6,
            sellTargetPrice = 72_000.0,
            sellQuantity = 4,
            soldQuantity = 1,
            averageSoldPrice = 72_000.0,
            remainingSellQuantity = 3,
            currentCash = 796_000.0,
            currentHoldingQuantity = 3,
            currentAveragePrice = 69_000.0,
            sellIntentCreatedAt = ZonedDateTime.parse("2026-06-02T09:01:00+09:00[Asia/Seoul]"),
            startedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00[Asia/Seoul]"),
            completedAt = null,
        )
    }
}

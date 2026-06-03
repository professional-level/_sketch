package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.`in`.FinalPriceBatingV1StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.LaorV4StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.QueryFinalPriceBatingV1StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.`in`.QueryLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusSnapshot
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatusCount
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GetStrategyExecutionOperationsStatusServiceTest {

    @Test
    fun `returns generated time and strategy execution operations snapshot`() = runBlocking {
        val snapshot = snapshot()
        val laorStrategies = listOf(laorView())
        val finalPriceStrategies = listOf(finalPriceView())
        val service = GetStrategyExecutionOperationsStatusService(
            strategyExecutionOperationsStatusPort = FakeStrategyExecutionOperationsStatusPort(snapshot),
            queryLaorV4StrategyExecutionUseCase = FakeQueryLaorV4StrategyExecutionUseCase(laorStrategies),
            queryFinalPriceBatingV1StrategyExecutionUseCase =
                FakeQueryFinalPriceBatingV1StrategyExecutionUseCase(finalPriceStrategies),
        ).apply {
            clock = Clock.fixed(
                Instant.parse("2026-06-02T01:00:00Z"),
                ZoneId.of("Asia/Seoul"),
            )
        }

        val result = service.execute()

        assertEquals(ZonedDateTime.parse("2026-06-02T10:00:00+09:00[Asia/Seoul]"), result.generatedAt)
        assertEquals(snapshot, result.snapshot)
        assertEquals(laorStrategies, result.activeLaorV4Strategies)
        assertEquals(finalPriceStrategies, result.activeFinalPriceBatingV1Strategies)
    }

    private fun snapshot(): StrategyExecutionOperationsStatusSnapshot {
        return StrategyExecutionOperationsStatusSnapshot(
            orderIntentOutboxStatusCounts = listOf(StrategyExecutionStatusCount("PENDING", 2)),
            strategyExecutionStartRequestCount = 3,
            strategyExecutionOrderEventTypeCounts = listOf(StrategyExecutionStatusCount("FILLED", 4)),
            laorV4StatusCounts = listOf(StrategyExecutionStatusCount("ACTIVE", 1)),
            finalPriceBatingV1StatusCounts = listOf(StrategyExecutionStatusCount("COMPLETED", 5)),
        )
    }

    private class FakeStrategyExecutionOperationsStatusPort(
        private val snapshot: StrategyExecutionOperationsStatusSnapshot,
    ) : StrategyExecutionOperationsStatusPort {
        override suspend fun loadStatus(): StrategyExecutionOperationsStatusSnapshot {
            return snapshot
        }
    }

    private class FakeQueryLaorV4StrategyExecutionUseCase(
        private val active: List<LaorV4StrategyExecutionView>,
    ) : QueryLaorV4StrategyExecutionUseCase {
        override suspend fun find(executionId: String): LaorV4StrategyExecutionView? {
            return active.firstOrNull { it.executionId == executionId }
        }

        override suspend fun findActive(): List<LaorV4StrategyExecutionView> {
            return active
        }
    }

    private class FakeQueryFinalPriceBatingV1StrategyExecutionUseCase(
        private val active: List<FinalPriceBatingV1StrategyExecutionView>,
    ) : QueryFinalPriceBatingV1StrategyExecutionUseCase {
        override suspend fun find(executionId: String): FinalPriceBatingV1StrategyExecutionView? {
            return active.firstOrNull { it.executionId == executionId }
        }

        override suspend fun findActive(): List<FinalPriceBatingV1StrategyExecutionView> {
            return active
        }
    }

    private fun laorView(): LaorV4StrategyExecutionView {
        return LaorV4StrategyExecutionView(
            executionId = "laor-v4:TQQQ",
            symbol = LaorV4StrategySymbol.TQQQ,
            status = StrategyExecutionLifecycleStatus.ACTIVE,
            cycleNo = 2,
            totalSplitCount = 20,
            firstBuyLimitPercentAbovePreviousClose = 12.0,
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

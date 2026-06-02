package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.out.FinalPriceBatingV1ExecutionState
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueryFinalPriceBatingV1StrategyExecutionServiceTest {

    @Test
    fun `returns final price bating operational state with cash and holding summary`() = runBlocking {
        val service = QueryFinalPriceBatingV1StrategyExecutionService(
            FakeStrategyExecutionStatePort(
                finalPriceStates = listOf(
                    state(
                        filledQuantity = 4,
                        averageFilledPrice = 95.0,
                        soldQuantity = 1,
                        averageSoldPrice = 100.0,
                    ),
                ),
            ),
        )

        val view = checkNotNull(service.find("FinalPriceBatingV1:005930"))

        assertEquals("FinalPriceBatingV1:005930", view.executionId)
        assertEquals(6L, view.remainingBuyQuantity)
        assertEquals(9L, view.remainingSellQuantity)
        assertEquals(720.0, view.currentCash)
        assertEquals(3L, view.currentHoldingQuantity)
        assertEquals(95.0, view.currentAveragePrice)
    }

    @Test
    fun `clears current average price when position is fully sold`() = runBlocking {
        val service = QueryFinalPriceBatingV1StrategyExecutionService(
            FakeStrategyExecutionStatePort(
                finalPriceStates = listOf(
                    state(
                        filledQuantity = 4,
                        averageFilledPrice = 95.0,
                        sellQuantity = 4,
                        soldQuantity = 4,
                        averageSoldPrice = 100.0,
                    ),
                ),
            ),
        )

        val view = checkNotNull(service.find("FinalPriceBatingV1:005930"))

        assertEquals(1_020.0, view.currentCash)
        assertEquals(0L, view.currentHoldingQuantity)
        assertNull(view.currentAveragePrice)
    }

    @Test
    fun `lists active final price bating states`() = runBlocking {
        val service = QueryFinalPriceBatingV1StrategyExecutionService(
            FakeStrategyExecutionStatePort(
                finalPriceStates = listOf(
                    state(executionId = "FinalPriceBatingV1:005930"),
                    state(executionId = "FinalPriceBatingV1:000660", symbol = "000660"),
                ),
            ),
        )

        val views = service.findActive()

        assertEquals(listOf("FinalPriceBatingV1:005930", "FinalPriceBatingV1:000660"), views.map { it.executionId })
    }

    private class FakeStrategyExecutionStatePort(
        private val finalPriceStates: List<FinalPriceBatingV1ExecutionState> = emptyList(),
    ) : StrategyExecutionStatePort {
        override suspend fun tryMarkStartRequested(idempotencyKey: String): Boolean = true

        override suspend fun findLaorV4Strategy(executionId: String): LaorV4ExecutionState? = null

        override suspend fun findActiveLaorV4Strategies(): List<LaorV4ExecutionState> = emptyList()

        override suspend fun saveLaorV4Strategy(state: LaorV4ExecutionState) = Unit

        override suspend fun findFinalPriceBatingV1Strategy(executionId: String): FinalPriceBatingV1ExecutionState? {
            return finalPriceStates.firstOrNull { it.executionId == executionId }
        }

        override suspend fun findActiveFinalPriceBatingV1Strategies(): List<FinalPriceBatingV1ExecutionState> {
            return finalPriceStates.filter { it.status == StrategyExecutionLifecycleStatus.ACTIVE }
        }

        override suspend fun saveFinalPriceBatingV1Strategy(state: FinalPriceBatingV1ExecutionState) = Unit
    }

    private fun state(
        executionId: String = "FinalPriceBatingV1:005930",
        symbol: String = "005930",
        filledQuantity: Long = 0,
        averageFilledPrice: Double? = null,
        sellQuantity: Long = 10,
        soldQuantity: Long = 0,
        averageSoldPrice: Double? = null,
    ): FinalPriceBatingV1ExecutionState {
        return FinalPriceBatingV1ExecutionState(
            executionId = executionId,
            symbol = symbol,
            market = "KRX",
            budget = 1_000.0,
            targetBuyPrice = 90.0,
            quantity = 10,
            status = StrategyExecutionLifecycleStatus.ACTIVE,
            filledQuantity = filledQuantity,
            averageFilledPrice = averageFilledPrice,
            sellTargetPrice = averageFilledPrice?.let { it * 1.03 },
            sellQuantity = sellQuantity,
            sellIntentCreatedAt = ZonedDateTime.parse("2026-06-02T09:01:00+09:00"),
            soldQuantity = soldQuantity,
            averageSoldPrice = averageSoldPrice,
            startedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
        )
    }
}

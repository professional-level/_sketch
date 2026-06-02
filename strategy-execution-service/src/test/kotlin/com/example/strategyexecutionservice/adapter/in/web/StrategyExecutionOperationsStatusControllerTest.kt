package com.example.strategyexecutionservice.adapter.`in`.web

import com.example.strategyexecutionservice.application.port.`in`.GetStrategyExecutionOperationsStatusUseCase
import com.example.strategyexecutionservice.application.port.`in`.StrategyExecutionOperationsStatusResult
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusSnapshot
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatusCount
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
}

package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusSnapshot
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatusCount
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
        val service = GetStrategyExecutionOperationsStatusService(
            FakeStrategyExecutionOperationsStatusPort(snapshot),
        ).apply {
            clock = Clock.fixed(
                Instant.parse("2026-06-02T01:00:00Z"),
                ZoneId.of("Asia/Seoul"),
            )
        }

        val result = service.execute()

        assertEquals(ZonedDateTime.parse("2026-06-02T10:00:00+09:00[Asia/Seoul]"), result.generatedAt)
        assertEquals(snapshot, result.snapshot)
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
}

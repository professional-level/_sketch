package com.example.strategyexecutionservice.adapter.out.persistence.entity

import com.example.strategyexecutionservice.application.port.out.FinalPriceBatingV1ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FinalPriceBatingV1StrategyExecutionEntityTest {

    @Test
    fun `maps final price bating execution state to entity and back`() {
        val state = FinalPriceBatingV1ExecutionState(
            executionId = "FinalPriceBatingV1:005930",
            symbol = "005930",
            market = "KRX",
            budget = 140_000.0,
            targetBuyPrice = 70_000.0,
            quantity = 2,
            status = StrategyExecutionLifecycleStatus.COMPLETED,
            filledQuantity = 2,
            averageFilledPrice = 71_000.0,
            startedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            completedAt = ZonedDateTime.parse("2026-06-02T09:30:00+09:00"),
        )

        val mapped = FinalPriceBatingV1StrategyExecutionEntity.from(state).toDto()

        assertEquals(state, mapped)
    }
}

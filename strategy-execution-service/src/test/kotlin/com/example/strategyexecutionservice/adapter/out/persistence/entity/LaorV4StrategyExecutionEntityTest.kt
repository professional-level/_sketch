package com.example.strategyexecutionservice.adapter.out.persistence.entity

import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class LaorV4StrategyExecutionEntityTest {

    @Test
    fun `maps laor execution state to entity and back`() {
        val lastExecutedAt = ZonedDateTime.parse("2026-06-02T09:30:00+09:00")
        val state = LaorV4ExecutionState(
            executionId = "laor-v4:TQQQ",
            symbol = LaorV4StrategySymbol.TQQQ,
            totalSplitCount = 20,
            firstBuyLimitPercentAbovePreviousClose = 12.0,
            autoRestart = false,
            cycleNo = 3,
            status = StrategyExecutionLifecycleStatus.COMPLETED,
            state = LaorV4StrategyState(
                mode = LaorV4StrategyMode.REVERSE,
                progressRound = 18.5,
                availableCash = 1_234.5,
                holdingQuantity = 10,
                averagePurchasePrice = 98.76,
                realizedProfitLoss = 123.45,
                reverseModeElapsedDays = 2,
            ),
            lastExecutionRunId = "run-20260602",
            lastExecutedAt = lastExecutedAt,
        )

        val mapped = LaorV4StrategyExecutionEntity.from(state).toDto()

        assertEquals(state, mapped)
    }
}

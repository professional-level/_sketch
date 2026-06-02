package com.example.strategyexecutionservice.application.port.`in`

import com.example.common.UseCase
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusSnapshot
import java.time.ZonedDateTime

@UseCase
interface GetStrategyExecutionOperationsStatusUseCase {
    suspend fun execute(): StrategyExecutionOperationsStatusResult
}

data class StrategyExecutionOperationsStatusResult(
    val generatedAt: ZonedDateTime,
    val snapshot: StrategyExecutionOperationsStatusSnapshot,
)

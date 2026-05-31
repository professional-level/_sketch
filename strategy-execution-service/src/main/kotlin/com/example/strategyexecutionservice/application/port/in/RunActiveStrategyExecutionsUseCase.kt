package com.example.strategyexecutionservice.application.port.`in`

import com.example.common.UseCase
import java.time.ZonedDateTime

@UseCase
interface RunActiveStrategyExecutionsUseCase {
    suspend fun execute(command: RunActiveStrategyExecutionsCommand): RunActiveStrategyExecutionsResult
}

data class RunActiveStrategyExecutionsCommand(
    val executionRunId: String,
    val requestedAt: ZonedDateTime,
) {
    init {
        require(executionRunId.isNotBlank()) { "executionRunId must not be blank" }
    }
}

data class RunActiveStrategyExecutionsResult(
    val executionRunId: String,
    val activeStrategyCount: Int,
    val executedStrategyCount: Int,
    val createdOrderIntentCount: Int,
)

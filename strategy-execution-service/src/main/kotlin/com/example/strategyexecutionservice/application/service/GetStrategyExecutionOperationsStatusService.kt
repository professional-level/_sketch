package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.GetStrategyExecutionOperationsStatusUseCase
import com.example.strategyexecutionservice.application.port.`in`.StrategyExecutionOperationsStatusResult
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusPort
import java.time.Clock
import java.time.ZonedDateTime

@UseCaseImpl
class GetStrategyExecutionOperationsStatusService(
    private val strategyExecutionOperationsStatusPort: StrategyExecutionOperationsStatusPort,
) : GetStrategyExecutionOperationsStatusUseCase {
    internal var clock: Clock = Clock.systemDefaultZone()

    override suspend fun execute(): StrategyExecutionOperationsStatusResult {
        return StrategyExecutionOperationsStatusResult(
            generatedAt = ZonedDateTime.now(clock),
            snapshot = strategyExecutionOperationsStatusPort.loadStatus(),
        )
    }
}

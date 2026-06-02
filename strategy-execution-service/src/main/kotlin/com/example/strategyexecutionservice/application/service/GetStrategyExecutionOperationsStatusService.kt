package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.GetStrategyExecutionOperationsStatusUseCase
import com.example.strategyexecutionservice.application.port.`in`.QueryFinalPriceBatingV1StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.`in`.QueryLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.`in`.StrategyExecutionOperationsStatusResult
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusPort
import java.time.Clock
import java.time.ZonedDateTime

@UseCaseImpl
class GetStrategyExecutionOperationsStatusService(
    private val strategyExecutionOperationsStatusPort: StrategyExecutionOperationsStatusPort,
    private val queryLaorV4StrategyExecutionUseCase: QueryLaorV4StrategyExecutionUseCase,
    private val queryFinalPriceBatingV1StrategyExecutionUseCase: QueryFinalPriceBatingV1StrategyExecutionUseCase,
) : GetStrategyExecutionOperationsStatusUseCase {
    internal var clock: Clock = Clock.systemDefaultZone()

    override suspend fun execute(): StrategyExecutionOperationsStatusResult {
        return StrategyExecutionOperationsStatusResult(
            generatedAt = ZonedDateTime.now(clock),
            snapshot = strategyExecutionOperationsStatusPort.loadStatus(),
            activeLaorV4Strategies = queryLaorV4StrategyExecutionUseCase.findActive(),
            activeFinalPriceBatingV1Strategies = queryFinalPriceBatingV1StrategyExecutionUseCase.findActive(),
        )
    }
}

package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionStatus
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState

@UseCaseImpl
class RegisterLaorV4StrategyExecutionService(
    private val strategyExecutionStatePort: StrategyExecutionStatePort,
) : RegisterLaorV4StrategyExecutionUseCase {

    override suspend fun execute(
        command: RegisterLaorV4StrategyExecutionCommand,
    ): RegisterLaorV4StrategyExecutionResult {
        val existingState = strategyExecutionStatePort.findLaorV4Strategy(command.executionId)
        if (existingState != null) {
            return existingState.toResult(RegisterLaorV4StrategyExecutionStatus.ALREADY_REGISTERED)
        }

        val registeredState = LaorV4ExecutionState(
            executionId = command.executionId,
            symbol = command.symbol,
            totalSplitCount = command.totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = command.firstBuyLimitPercentAbovePreviousClose,
            autoRestart = command.autoRestart,
            state = LaorV4StrategyState(availableCash = command.budget),
        )
        strategyExecutionStatePort.saveLaorV4Strategy(registeredState)
        return registeredState.toResult(RegisterLaorV4StrategyExecutionStatus.REGISTERED)
    }

    private fun LaorV4ExecutionState.toResult(
        status: RegisterLaorV4StrategyExecutionStatus,
    ): RegisterLaorV4StrategyExecutionResult {
        return RegisterLaorV4StrategyExecutionResult(
            executionId = executionId,
            status = status,
            symbol = symbol,
            budget = state.availableCash,
            totalSplitCount = totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = firstBuyLimitPercentAbovePreviousClose,
            autoRestart = autoRestart,
        )
    }
}

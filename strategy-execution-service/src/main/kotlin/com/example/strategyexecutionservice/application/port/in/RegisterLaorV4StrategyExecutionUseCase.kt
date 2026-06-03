package com.example.strategyexecutionservice.application.port.`in`

import com.example.common.UseCase
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol

@UseCase
interface RegisterLaorV4StrategyExecutionUseCase {
    suspend fun execute(command: RegisterLaorV4StrategyExecutionCommand): RegisterLaorV4StrategyExecutionResult
}

data class RegisterLaorV4StrategyExecutionCommand(
    val executionId: String,
    val symbol: LaorV4StrategySymbol,
    val budget: Double,
    val totalSplitCount: Int,
    val firstBuyLimitPercentAbovePreviousClose: Double,
    val autoRestart: Boolean = true,
) {
    init {
        require(executionId.isNotBlank()) { "executionId must not be blank" }
        require(budget > 0.0) { "budget must be positive" }
        require(totalSplitCount > 1) { "totalSplitCount must be greater than 1" }
        require(
            firstBuyLimitPercentAbovePreviousClose in
                LaorV4StrategyConfig.MIN_FIRST_BUY_LIMIT_PERCENT_ABOVE_PREVIOUS_CLOSE..
                LaorV4StrategyConfig.MAX_FIRST_BUY_LIMIT_PERCENT_ABOVE_PREVIOUS_CLOSE,
        ) { "firstBuyLimitPercentAbovePreviousClose must be between 10 and 15" }
    }
}

data class RegisterLaorV4StrategyExecutionResult(
    val executionId: String,
    val status: RegisterLaorV4StrategyExecutionStatus,
    val symbol: LaorV4StrategySymbol,
    val budget: Double,
    val totalSplitCount: Int,
    val firstBuyLimitPercentAbovePreviousClose: Double,
    val autoRestart: Boolean,
)

enum class RegisterLaorV4StrategyExecutionStatus {
    REGISTERED,
    ALREADY_REGISTERED,
}

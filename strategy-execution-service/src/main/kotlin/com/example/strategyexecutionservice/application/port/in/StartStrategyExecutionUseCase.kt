package com.example.strategyexecutionservice.application.port.`in`

import com.example.common.UseCase
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyProfile
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.time.ZonedDateTime

@UseCase
interface StartStrategyExecutionUseCase {
    suspend fun execute(command: StartStrategyExecutionCommand): StartStrategyExecutionResult
}

sealed class StartStrategyExecutionCommand {
    abstract val executionId: String
    abstract val idempotencyKey: String
    abstract val strategyVersion: String
    abstract val symbol: String
    abstract val market: String
    abstract val budget: Double
    abstract val requestedAt: ZonedDateTime

    data class LaorV4(
        override val executionId: String,
        override val idempotencyKey: String,
        override val strategyVersion: String,
        val strategySymbol: LaorV4StrategySymbol,
        override val market: String,
        override val budget: Double,
        val totalSplitCount: Int,
        val firstBuyLimitPercentAbovePreviousClose: Double,
        val autoRestart: Boolean = true,
        override val requestedAt: ZonedDateTime,
    ) : StartStrategyExecutionCommand() {
        override val symbol: String = strategySymbol.ticker

        init {
            require(executionId.isNotBlank()) { "executionId must not be blank" }
            require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
            require(strategyVersion.isNotBlank()) { "strategyVersion must not be blank" }
            require(market.isNotBlank()) { "market must not be blank" }
            require(budget > 0.0) { "budget must be positive" }
            LaorV4StrategyProfile.validate(strategySymbol, totalSplitCount)
            require(
                firstBuyLimitPercentAbovePreviousClose in
                    LaorV4StrategyConfig.MIN_FIRST_BUY_LIMIT_PERCENT_ABOVE_PREVIOUS_CLOSE..
                    LaorV4StrategyConfig.MAX_FIRST_BUY_LIMIT_PERCENT_ABOVE_PREVIOUS_CLOSE,
            ) { "firstBuyLimitPercentAbovePreviousClose must be between 10 and 15" }
        }
    }

    data class FinalPriceBatingV1(
        override val executionId: String,
        override val idempotencyKey: String,
        override val strategyVersion: String,
        override val symbol: String,
        override val market: String,
        override val budget: Double,
        val targetBuyPrice: Double,
        val quantityPolicy: String,
        override val requestedAt: ZonedDateTime,
    ) : StartStrategyExecutionCommand() {
        init {
            require(executionId.isNotBlank()) { "executionId must not be blank" }
            require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
            require(strategyVersion.isNotBlank()) { "strategyVersion must not be blank" }
            require(symbol.isNotBlank()) { "symbol must not be blank" }
            require(market.isNotBlank()) { "market must not be blank" }
            require(budget > 0.0) { "budget must be positive" }
            require(targetBuyPrice > 0.0) { "targetBuyPrice must be positive" }
            require(quantityPolicy.isNotBlank()) { "quantityPolicy must not be blank" }
        }
    }
}

data class StartStrategyExecutionResult(
    val executionId: String,
    val status: StartStrategyExecutionStatus,
    val createdOrderIntentCount: Int,
)

enum class StartStrategyExecutionStatus {
    STARTED,
    SKIPPED_DUPLICATE,
}

package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.LaorV4State
import com.example.strategyexecutionservice.application.port.`in`.MarketSnapshot
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.out.OrderIntentMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentPort
import com.example.strategyexecutionservice.domain.strategy.execution.OrderIntent
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionId
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyMarketSnapshot
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4Strategy
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.util.UUID

@UseCaseImpl
class RunStrategyExecutionService(
    private val orderIntentPort: OrderIntentPort,
) : RunStrategyExecutionUseCase {

    override suspend fun execute(command: RunStrategyExecutionCommand): RunStrategyExecutionResult {
        val strategy = command.toStrategyExecution()
        val plan = strategy.generateOrders(command.market.toDomain())
        val createdAt = ZonedDateTime.now()

        orderIntentPort.publishAll(
            plan.orders.mapIndexed { index, orderIntent ->
                orderIntent.toMessage(
                    strategyType = plan.execution.type,
                    executionRunId = command.executionRunId,
                    orderIndex = index,
                    createdAt = createdAt,
                )
            },
        )

        return RunStrategyExecutionResult(
            executionId = command.executionId,
            executionRunId = command.executionRunId,
            createdOrderIntentCount = plan.orders.size,
        )
    }

    private fun RunStrategyExecutionCommand.toStrategyExecution(): LaorV4Strategy {
        return when (this) {
            is RunStrategyExecutionCommand.LaorV4 -> LaorV4Strategy(
                id = StrategyExecutionId(executionId),
                config = LaorV4StrategyConfig(
                    symbol = symbol,
                    totalSplitCount = totalSplitCount,
                    firstBuyLimitMultiplier = firstBuyLimitMultiplier,
                ),
                state = state.toDomain(),
            )
        }
    }

    private val RunStrategyExecutionCommand.market: MarketSnapshot
        get() = when (this) {
            is RunStrategyExecutionCommand.LaorV4 -> market
        }

    private fun LaorV4State.toDomain(): LaorV4StrategyState {
        return LaorV4StrategyState(
            mode = mode,
            progressRound = progressRound,
            availableCash = availableCash,
            holdingQuantity = holdingQuantity,
            averagePurchasePrice = averagePurchasePrice,
            realizedProfitLoss = realizedProfitLoss,
            reverseModeElapsedDays = reverseModeElapsedDays,
        )
    }

    private fun MarketSnapshot.toDomain(): StrategyMarketSnapshot {
        return StrategyMarketSnapshot(
            previousClose = previousClose,
            recentClosePrices = recentClosePrices,
        )
    }

    private fun OrderIntent.toMessage(
        strategyType: com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType,
        executionRunId: String,
        orderIndex: Int,
        createdAt: ZonedDateTime,
    ): OrderIntentMessage {
        val idempotencyKey = "${executionId.value}:$executionRunId:$tag:$orderIndex"
        return OrderIntentMessage(
            eventId = UUID.nameUUIDFromBytes(idempotencyKey.toByteArray(StandardCharsets.UTF_8)),
            strategyExecutionId = executionId.value,
            strategyType = strategyType,
            symbol = symbol,
            side = side,
            orderType = type,
            price = price,
            quantity = quantity,
            orderTag = tag,
            idempotencyKey = idempotencyKey,
            createdAt = createdAt,
        )
    }
}

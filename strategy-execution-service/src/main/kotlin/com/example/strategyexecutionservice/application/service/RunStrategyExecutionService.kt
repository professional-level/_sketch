package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.LaorV4State
import com.example.strategyexecutionservice.application.port.`in`.MarketSnapshot
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.out.OrderIntentMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentPort
import com.example.strategyexecutionservice.application.port.out.OrderTradingEnvironment
import com.example.strategyexecutionservice.application.port.out.TradingCalendarPort
import com.example.strategyexecutionservice.application.port.out.TradingMarket
import com.example.strategyexecutionservice.domain.strategy.execution.OrderIntent
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType
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
    private val tradingCalendarPort: TradingCalendarPort,
    private val tradingEnvironmentResolver: OrderIntentTradingEnvironmentResolver = OrderIntentTradingEnvironmentResolver(),
) : RunStrategyExecutionUseCase {

    override suspend fun execute(command: RunStrategyExecutionCommand): RunStrategyExecutionResult {
        val tradingMarket = command.tradingMarket()
        val requestedDate = tradingCalendarPort.tradingDate(tradingMarket, command.requestedAt)
        if (!tradingCalendarPort.isTradingDay(tradingMarket, requestedDate)) {
            return command.skipped("US market is closed on $requestedDate")
        }

        val orderRequestedAt = tradingCalendarPort.orderSessionStartAt(tradingMarket, command.requestedAt)
        if (orderRequestedAt.toInstant().isAfter(command.requestedAt.toInstant())) {
            return command.skipped("US market order session is not open until $orderRequestedAt")
        }

        val strategy = command.toStrategyExecution()
        val plan = strategy.generateOrders(command.market.toDomain())
        val createdAt = command.requestedAt

        orderIntentPort.publishAll(
            plan.orders.mapIndexed { index, orderIntent ->
                orderIntent.toMessage(
                    strategyType = plan.execution.type,
                    executionRunId = command.executionRunId,
                    orderIndex = index,
                    createdAt = createdAt,
                    tradingEnvironment = tradingEnvironmentResolver.resolve(command.executionId),
                    market = command.marketCode(),
                    strategyVersion = "v1",
                )
            },
        )

        return RunStrategyExecutionResult(
            executionId = command.executionId,
            executionRunId = command.executionRunId,
            createdOrderIntentCount = plan.orders.size,
            plannedState = (plan.execution as? LaorV4Strategy)?.state?.toApplication(),
        )
    }

    private fun RunStrategyExecutionCommand.skipped(reason: String): RunStrategyExecutionResult {
        return RunStrategyExecutionResult(
            executionId = executionId,
            executionRunId = executionRunId,
            createdOrderIntentCount = 0,
            skippedReason = reason,
        )
    }

    private fun RunStrategyExecutionCommand.toStrategyExecution(): LaorV4Strategy {
        return when (this) {
            is RunStrategyExecutionCommand.LaorV4 -> LaorV4Strategy(
                id = StrategyExecutionId(executionId),
                config = LaorV4StrategyConfig(
                    symbol = symbol,
                    totalSplitCount = totalSplitCount,
                    firstBuyLimitPercentAbovePreviousClose = firstBuyLimitPercentAbovePreviousClose,
                ),
                state = state.toDomain(),
            )
        }
    }

    private fun RunStrategyExecutionCommand.tradingMarket(): TradingMarket {
        return when (this) {
            is RunStrategyExecutionCommand.LaorV4 -> TradingMarket.US
        }
    }

    private val RunStrategyExecutionCommand.market: MarketSnapshot
        get() = when (this) {
            is RunStrategyExecutionCommand.LaorV4 -> market
        }

    private fun RunStrategyExecutionCommand.marketCode(): String {
        return when (this) {
            is RunStrategyExecutionCommand.LaorV4 -> "US"
        }
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

    private fun LaorV4StrategyState.toApplication(): LaorV4State {
        return LaorV4State(
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
        strategyType: StrategyExecutionType,
        executionRunId: String,
        orderIndex: Int,
        createdAt: ZonedDateTime,
        tradingEnvironment: OrderTradingEnvironment,
        market: String,
        strategyVersion: String,
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
            tradingEnvironment = tradingEnvironment,
            executionRunId = executionRunId,
            orderIndex = orderIndex,
            market = market,
            strategyVersion = strategyVersion,
        )
    }
}

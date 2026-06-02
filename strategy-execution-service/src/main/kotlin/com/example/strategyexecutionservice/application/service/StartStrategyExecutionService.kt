package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.LaorV4State
import com.example.strategyexecutionservice.application.port.`in`.MarketSnapshot
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionStatus
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RunStrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionStatus
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.out.MarketDataPort
import com.example.strategyexecutionservice.application.port.out.OrderIntentMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.execution.OrderType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.floor

@UseCaseImpl
class StartStrategyExecutionService(
    private val strategyExecutionStatePort: StrategyExecutionStatePort,
    private val registerLaorV4StrategyExecutionUseCase: RegisterLaorV4StrategyExecutionUseCase,
    private val runStrategyExecutionUseCase: RunStrategyExecutionUseCase,
    private val marketDataPort: MarketDataPort,
    private val orderIntentPort: OrderIntentPort,
    private val tradingEnvironmentResolver: OrderIntentTradingEnvironmentResolver = OrderIntentTradingEnvironmentResolver(),
) : StartStrategyExecutionUseCase {

    override suspend fun execute(command: StartStrategyExecutionCommand): StartStrategyExecutionResult {
        val started = strategyExecutionStatePort.tryMarkStartRequested(command.idempotencyKey)
        if (!started) {
            return StartStrategyExecutionResult(
                executionId = command.executionId,
                status = StartStrategyExecutionStatus.SKIPPED_DUPLICATE,
                createdOrderIntentCount = 0,
            )
        }

        return when (command) {
            is StartStrategyExecutionCommand.LaorV4 -> startLaor(command)
            is StartStrategyExecutionCommand.FinalPriceBatingV1 -> startFinalPriceBating(command)
        }
    }

    private suspend fun startLaor(
        command: StartStrategyExecutionCommand.LaorV4,
    ): StartStrategyExecutionResult {
        val registration = registerLaorV4StrategyExecutionUseCase.execute(
            RegisterLaorV4StrategyExecutionCommand(
                executionId = command.executionId,
                symbol = command.strategySymbol,
                budget = command.budget,
                totalSplitCount = command.totalSplitCount,
                firstBuyLimitMultiplier = command.firstBuyLimitMultiplier,
                autoRestart = command.autoRestart,
            ),
        )
        if (registration.status == RegisterLaorV4StrategyExecutionStatus.ALREADY_REGISTERED) {
            return StartStrategyExecutionResult(
                executionId = command.executionId,
                status = StartStrategyExecutionStatus.SKIPPED_DUPLICATE,
                createdOrderIntentCount = 0,
            )
        }

        val market = marketDataPort.getMarketSnapshot(command.symbol, recentCloseCount = 5)
        val result = runStrategyExecutionUseCase.execute(
            RunStrategyExecutionCommand.LaorV4(
                executionId = command.executionId,
                executionRunId = command.idempotencyKey,
                symbol = command.strategySymbol,
                totalSplitCount = command.totalSplitCount,
                firstBuyLimitMultiplier = command.firstBuyLimitMultiplier,
                state = LaorV4State(availableCash = command.budget),
                market = MarketSnapshot(
                    previousClose = market.previousClose,
                    recentClosePrices = market.recentClosePrices,
                ),
            ),
        )
        return StartStrategyExecutionResult(
            executionId = command.executionId,
            status = StartStrategyExecutionStatus.STARTED,
            createdOrderIntentCount = result.createdOrderIntentCount,
        )
    }

    private suspend fun startFinalPriceBating(
        command: StartStrategyExecutionCommand.FinalPriceBatingV1,
    ): StartStrategyExecutionResult {
        val quantity = floor(command.budget / command.targetBuyPrice).toLong()
        if (quantity <= 0) {
            return StartStrategyExecutionResult(
                executionId = command.executionId,
                status = StartStrategyExecutionStatus.STARTED,
                createdOrderIntentCount = 0,
            )
        }

        val idempotencyKey = "${command.idempotencyKey}:ENTRY_BUY:0"
        orderIntentPort.publishAll(
            listOf(
                OrderIntentMessage(
                    eventId = UUID.nameUUIDFromBytes(idempotencyKey.toByteArray(StandardCharsets.UTF_8)),
                    strategyExecutionId = command.executionId,
                    strategyType = StrategyExecutionType.FINAL_PRICE_BATING_V1_STRATEGY,
                    symbol = command.symbol,
                    side = OrderSide.BUY,
                    orderType = OrderType.LIMIT,
                    price = command.targetBuyPrice,
                    quantity = quantity,
                    orderTag = "ENTRY_BUY",
                    idempotencyKey = idempotencyKey,
                    createdAt = command.requestedAt,
                    tradingEnvironment = tradingEnvironmentResolver.resolve(command.executionId),
                ),
            ),
        )

        return StartStrategyExecutionResult(
            executionId = command.executionId,
            status = StartStrategyExecutionStatus.STARTED,
            createdOrderIntentCount = 1,
        )
    }
}

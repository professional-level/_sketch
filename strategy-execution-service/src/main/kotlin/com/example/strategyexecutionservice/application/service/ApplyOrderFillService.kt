package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillCommand
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillResult
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillStatus
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillUseCase
import com.example.strategyexecutionservice.application.port.`in`.OrderFillKind
import com.example.strategyexecutionservice.application.port.out.MarketDataPort
import com.example.strategyexecutionservice.application.port.out.OrderIntentMessage
import com.example.strategyexecutionservice.application.port.out.OrderIntentPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventRecord
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventType
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.execution.OrderType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionFill
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionId
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4Strategy
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import java.nio.charset.StandardCharsets
import java.util.UUID

@UseCaseImpl
class ApplyOrderFillService(
    private val strategyExecutionStatePort: StrategyExecutionStatePort,
    private val marketDataPort: MarketDataPort,
    private val orderEventPort: StrategyExecutionOrderEventPort,
    private val orderIntentPort: OrderIntentPort,
    private val tradingEnvironmentResolver: OrderIntentTradingEnvironmentResolver = OrderIntentTradingEnvironmentResolver(),
) : ApplyOrderFillUseCase {

    override suspend fun execute(command: ApplyOrderFillCommand): ApplyOrderFillResult {
        val current = strategyExecutionStatePort.findLaorV4Strategy(command.strategyExecutionId)
        if (current == null) {
            return applyFinalPriceBatingFill(command)
        }

        if (!orderEventPort.tryRecord(command.toRecord())) {
            return ApplyOrderFillResult(command.strategyExecutionId, ApplyOrderFillStatus.SKIPPED_DUPLICATE)
        }

        if (current.status == StrategyExecutionLifecycleStatus.COMPLETED) {
            return ApplyOrderFillResult(command.strategyExecutionId, ApplyOrderFillStatus.IGNORED_COMPLETED)
        }

        val strategy = LaorV4Strategy(
            id = StrategyExecutionId(current.executionId),
            config = LaorV4StrategyConfig(
                symbol = current.symbol,
                totalSplitCount = current.totalSplitCount,
                firstBuyLimitPercentAbovePreviousClose = current.firstBuyLimitPercentAbovePreviousClose,
            ),
            state = current.state,
        )
        val market = marketDataPort.getMarketSnapshot(
            symbol = current.symbol.ticker,
            asOfDate = command.filledAt.toLocalDate(),
            recentCloseCount = 5,
        )
        val next = strategy.applyFills(
            fills = listOf(
                StrategyExecutionFill(
                    side = command.side,
                    price = command.filledPrice,
                    quantity = command.filledQuantity,
                    tag = command.orderTag,
                    advancesProgressRound = command.fillKind == OrderFillKind.FILLED,
                ),
            ),
            closePrice = market.previousClose,
        )

        val cycleClosed = current.state.holdingQuantity > 0 && next.state.holdingQuantity == 0L
        val nextStatus = if (cycleClosed && !current.autoRestart) {
            StrategyExecutionLifecycleStatus.COMPLETED
        } else {
            current.status
        }
        val nextCycleNo = if (cycleClosed && current.autoRestart) current.cycleNo + 1 else current.cycleNo

        strategyExecutionStatePort.saveLaorV4Strategy(
            current.copy(
                state = next.state,
                cycleNo = nextCycleNo,
                status = nextStatus,
            ),
        )

        return ApplyOrderFillResult(command.strategyExecutionId, ApplyOrderFillStatus.APPLIED)
    }

    private suspend fun applyFinalPriceBatingFill(command: ApplyOrderFillCommand): ApplyOrderFillResult {
        val current = strategyExecutionStatePort.findFinalPriceBatingV1Strategy(command.strategyExecutionId)
            ?: return ApplyOrderFillResult(command.strategyExecutionId, ApplyOrderFillStatus.STRATEGY_NOT_FOUND)

        if (!orderEventPort.tryRecord(command.toRecord())) {
            return ApplyOrderFillResult(command.strategyExecutionId, ApplyOrderFillStatus.SKIPPED_DUPLICATE)
        }

        if (current.status == StrategyExecutionLifecycleStatus.COMPLETED) {
            return ApplyOrderFillResult(command.strategyExecutionId, ApplyOrderFillStatus.IGNORED_COMPLETED)
        }

        return when (command.side) {
            OrderSide.BUY -> applyFinalPriceBatingEntryBuyFill(current, command)
            OrderSide.SELL -> applyFinalPriceBatingExitSellFill(current, command)
        }
    }

    private suspend fun applyFinalPriceBatingEntryBuyFill(
        current: com.example.strategyexecutionservice.application.port.out.FinalPriceBatingV1ExecutionState,
        command: ApplyOrderFillCommand,
    ): ApplyOrderFillResult {
        val nextFilledQuantity = (current.filledQuantity + command.filledQuantity).coerceAtMost(current.quantity)
        val addedQuantity = nextFilledQuantity - current.filledQuantity
        val nextAverageFilledPrice = weightedAverage(
            currentQuantity = current.filledQuantity,
            currentAverage = current.averageFilledPrice,
            addedQuantity = addedQuantity,
            addedPrice = command.filledPrice,
        )
        val entryCompleted = command.fillKind == OrderFillKind.FILLED || nextFilledQuantity >= current.quantity
        val shouldCreateSellIntent = entryCompleted && current.sellIntentCreatedAt == null && nextFilledQuantity > 0
        val sellTargetPrice = if (shouldCreateSellIntent) {
            calculateFinalPriceBatingSellTargetPrice(nextAverageFilledPrice)
        } else {
            current.sellTargetPrice
        }
        if (shouldCreateSellIntent) {
            publishFinalPriceBatingSellIntent(
                current = current,
                price = sellTargetPrice ?: command.filledPrice,
                quantity = nextFilledQuantity,
                createdAt = command.filledAt,
            )
        }
        strategyExecutionStatePort.saveFinalPriceBatingV1Strategy(
            current.copy(
                filledQuantity = nextFilledQuantity,
                averageFilledPrice = nextAverageFilledPrice,
                sellTargetPrice = sellTargetPrice,
                sellQuantity = if (shouldCreateSellIntent) nextFilledQuantity else current.sellQuantity,
                sellIntentCreatedAt = if (shouldCreateSellIntent) command.filledAt else current.sellIntentCreatedAt,
            ),
        )

        return ApplyOrderFillResult(command.strategyExecutionId, ApplyOrderFillStatus.APPLIED)
    }

    private suspend fun applyFinalPriceBatingExitSellFill(
        current: com.example.strategyexecutionservice.application.port.out.FinalPriceBatingV1ExecutionState,
        command: ApplyOrderFillCommand,
    ): ApplyOrderFillResult {
        val expectedSellQuantity = when {
            current.sellQuantity > 0 -> current.sellQuantity
            current.filledQuantity > 0 -> current.filledQuantity
            else -> command.filledQuantity
        }
        val nextSoldQuantity = (current.soldQuantity + command.filledQuantity).coerceAtMost(expectedSellQuantity)
        val addedQuantity = nextSoldQuantity - current.soldQuantity
        val nextAverageSoldPrice = weightedAverage(
            currentQuantity = current.soldQuantity,
            currentAverage = current.averageSoldPrice,
            addedQuantity = addedQuantity,
            addedPrice = command.filledPrice,
        )
        val exitCompleted = command.fillKind == OrderFillKind.FILLED || nextSoldQuantity >= expectedSellQuantity
        strategyExecutionStatePort.saveFinalPriceBatingV1Strategy(
            current.copy(
                sellQuantity = expectedSellQuantity,
                soldQuantity = nextSoldQuantity,
                averageSoldPrice = nextAverageSoldPrice,
                status = if (exitCompleted) StrategyExecutionLifecycleStatus.COMPLETED else current.status,
                completedAt = if (exitCompleted) command.filledAt else current.completedAt,
            ),
        )

        return ApplyOrderFillResult(command.strategyExecutionId, ApplyOrderFillStatus.APPLIED)
    }

    private suspend fun publishFinalPriceBatingSellIntent(
        current: com.example.strategyexecutionservice.application.port.out.FinalPriceBatingV1ExecutionState,
        price: Double,
        quantity: Long,
        createdAt: java.time.ZonedDateTime,
    ) {
        val idempotencyKey = "${current.executionId}:FINAL_PRICE_BATING_V1_SELL:0"
        orderIntentPort.publishAll(
            listOf(
                OrderIntentMessage(
                    eventId = UUID.nameUUIDFromBytes(idempotencyKey.toByteArray(StandardCharsets.UTF_8)),
                    strategyExecutionId = current.executionId,
                    strategyType = StrategyExecutionType.FINAL_PRICE_BATING_V1_STRATEGY,
                    symbol = current.symbol,
                    side = OrderSide.SELL,
                    orderType = OrderType.LIMIT,
                    price = price,
                    quantity = quantity,
                    orderTag = FINAL_PRICE_BATING_SELL_ORDER_TAG,
                    idempotencyKey = idempotencyKey,
                    createdAt = createdAt,
                    tradingEnvironment = tradingEnvironmentResolver.resolve(current.executionId),
                    executionRunId = "${current.executionId}:SELL_AFTER_ENTRY",
                    orderIndex = 0,
                    market = current.market,
                    strategyVersion = "v1",
                ),
            ),
        )
    }

    private fun calculateFinalPriceBatingSellTargetPrice(averageFilledPrice: Double?): Double {
        return checkNotNull(averageFilledPrice) { "averageFilledPrice is required to create sell intent" } *
            (1 + FINAL_PRICE_BATING_TAKE_PROFIT_MARGIN)
    }

    private fun weightedAverage(
        currentQuantity: Long,
        currentAverage: Double?,
        addedQuantity: Long,
        addedPrice: Double,
    ): Double {
        if (addedQuantity <= 0) return currentAverage ?: addedPrice
        if (currentQuantity <= 0 || currentAverage == null) return addedPrice
        val totalQuantity = currentQuantity + addedQuantity
        return ((currentAverage * currentQuantity) + (addedPrice * addedQuantity)) / totalQuantity
    }
}

private const val FINAL_PRICE_BATING_TAKE_PROFIT_MARGIN = 0.03
private const val FINAL_PRICE_BATING_SELL_ORDER_TAG = "FINAL_PRICE_BATING_V1_SELL"

private fun ApplyOrderFillCommand.toRecord(): StrategyExecutionOrderEventRecord {
        return StrategyExecutionOrderEventRecord(
            eventId = eventId,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = strategyExecutionId,
            orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId,
        type = fillKind.toEventType(),
        side = side,
        price = filledPrice,
        quantity = filledQuantity,
        orderTag = orderTag,
        occurredAt = filledAt,
    )
}

private fun OrderFillKind.toEventType(): StrategyExecutionOrderEventType {
    return when (this) {
        OrderFillKind.FILLED -> StrategyExecutionOrderEventType.FILLED
        OrderFillKind.PARTIALLY_FILLED -> StrategyExecutionOrderEventType.PARTIALLY_FILLED
    }
}

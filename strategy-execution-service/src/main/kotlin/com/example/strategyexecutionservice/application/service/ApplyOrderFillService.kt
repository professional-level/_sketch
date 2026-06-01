package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillCommand
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillResult
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillStatus
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillUseCase
import com.example.strategyexecutionservice.application.port.`in`.OrderFillKind
import com.example.strategyexecutionservice.application.port.out.MarketDataPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventRecord
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventType
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatePort
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionFill
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionId
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4Strategy
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig

@UseCaseImpl
class ApplyOrderFillService(
    private val strategyExecutionStatePort: StrategyExecutionStatePort,
    private val marketDataPort: MarketDataPort,
    private val orderEventPort: StrategyExecutionOrderEventPort,
) : ApplyOrderFillUseCase {

    override suspend fun execute(command: ApplyOrderFillCommand): ApplyOrderFillResult {
        val current = strategyExecutionStatePort.findLaorV4Strategy(command.strategyExecutionId)
            ?: return ApplyOrderFillResult(command.strategyExecutionId, ApplyOrderFillStatus.STRATEGY_NOT_FOUND)

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
                firstBuyLimitMultiplier = current.firstBuyLimitMultiplier,
            ),
            state = current.state,
        )
        val market = marketDataPort.getMarketSnapshot(current.symbol.ticker, recentCloseCount = 5)
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
}

private fun ApplyOrderFillCommand.toRecord(): StrategyExecutionOrderEventRecord {
    return StrategyExecutionOrderEventRecord(
        eventId = eventId,
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

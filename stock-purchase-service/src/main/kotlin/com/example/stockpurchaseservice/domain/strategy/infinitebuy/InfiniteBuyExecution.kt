package com.example.stockpurchaseservice.domain.strategy.infinitebuy

import com.example.stockpurchaseservice.domain.strategy.execution.OrderIntent
import com.example.stockpurchaseservice.domain.strategy.execution.OrderSide
import com.example.stockpurchaseservice.domain.strategy.execution.OrderType
import com.example.stockpurchaseservice.domain.strategy.execution.StrategyExecutionFill
import com.example.stockpurchaseservice.domain.strategy.execution.StrategyExecutionId
import com.example.stockpurchaseservice.domain.strategy.execution.StrategyExecutionStatus
import com.example.stockpurchaseservice.domain.strategy.execution.StrategyExecutionType
import com.example.stockpurchaseservice.domain.strategy.execution.StrategyMarketSnapshot
import com.example.stockpurchaseservice.domain.strategy.execution.StrategyOrderPlan
import com.example.stockpurchaseservice.domain.strategy.execution.TradingStrategyExecution

data class InfiniteBuyExecution(
    override val id: StrategyExecutionId,
    val config: InfiniteBuyConfig,
    val state: InfiniteBuyState,
    override val status: StrategyExecutionStatus = StrategyExecutionStatus.ACTIVE,
) : TradingStrategyExecution {

    override val type: StrategyExecutionType = StrategyExecutionType.INFINITE_BUY_V4

    override fun generateOrders(market: StrategyMarketSnapshot): StrategyOrderPlan {
        require(status == StrategyExecutionStatus.ACTIVE) { "closed execution cannot generate orders" }

        val planningState = state.forOrderGeneration(config)
        val orders = InfiniteBuyEngine.generateOrders(
            config = config,
            state = planningState,
            market = InfiniteBuyMarket(
                previousClose = market.previousClose,
                recentClosePrices = market.recentClosePrices,
            ),
        ).map { it.toOrderIntent(id) }

        return StrategyOrderPlan(
            execution = copy(state = planningState),
            orders = orders,
        )
    }

    override fun applyFills(
        fills: List<StrategyExecutionFill>,
        closePrice: Double,
    ): InfiniteBuyExecution {
        require(status == StrategyExecutionStatus.ACTIVE) { "closed execution cannot apply fills" }

        val nextState = InfiniteBuyEngine.applyFills(
            config = config,
            state = state,
            fills = fills.map { it.toExecutedFill() },
            closePrice = closePrice,
        )

        return copy(state = nextState)
    }

    private fun InfiniteBuyState.forOrderGeneration(config: InfiniteBuyConfig): InfiniteBuyState {
        return if (mode == TradeMode.NORMAL && t > config.splits - 1) {
            copy(mode = TradeMode.REVERSE, reverseDays = 0)
        } else {
            this
        }
    }

    private fun PlannedOrder.toOrderIntent(executionId: StrategyExecutionId): OrderIntent {
        return OrderIntent(
            executionId = executionId,
            side = side.toOrderSide(),
            type = type.toOrderType(),
            price = price,
            quantity = quantity,
            tag = tag.name,
        )
    }

    private fun StrategyExecutionFill.toExecutedFill(): ExecutedFill {
        return ExecutedFill(
            side = side.toTradeSide(),
            price = price,
            quantity = quantity,
            tag = OrderTag.valueOf(tag),
        )
    }

    private fun TradeSide.toOrderSide(): OrderSide {
        return when (this) {
            TradeSide.BUY -> OrderSide.BUY
            TradeSide.SELL -> OrderSide.SELL
        }
    }

    private fun TradeOrderType.toOrderType(): OrderType {
        return when (this) {
            TradeOrderType.LOC -> OrderType.LOC
            TradeOrderType.MOC -> OrderType.MOC
            TradeOrderType.LIMIT -> OrderType.LIMIT
        }
    }

    private fun OrderSide.toTradeSide(): TradeSide {
        return when (this) {
            OrderSide.BUY -> TradeSide.BUY
            OrderSide.SELL -> TradeSide.SELL
        }
    }
}

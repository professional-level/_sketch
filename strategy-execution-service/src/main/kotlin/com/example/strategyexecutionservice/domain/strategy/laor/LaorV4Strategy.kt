package com.example.strategyexecutionservice.domain.strategy.laor

import com.example.strategyexecutionservice.domain.strategy.execution.OrderIntent
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.execution.OrderType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionFill
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionId
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionStatus
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyExecutionType
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyMarketSnapshot
import com.example.strategyexecutionservice.domain.strategy.execution.StrategyOrderPlan
import com.example.strategyexecutionservice.domain.strategy.execution.TradingStrategyExecution

data class LaorV4Strategy(
    override val id: StrategyExecutionId,
    val config: LaorV4StrategyConfig,
    val state: LaorV4StrategyState,
    override val status: StrategyExecutionStatus = StrategyExecutionStatus.ACTIVE,
) : TradingStrategyExecution {

    override val type: StrategyExecutionType = StrategyExecutionType.LAOR_V4_STRATEGY

    override fun generateOrders(market: StrategyMarketSnapshot): StrategyOrderPlan {
        require(status == StrategyExecutionStatus.ACTIVE) { "closed strategy cannot generate orders" }

        val planningState = state.forOrderGeneration(config)
        val orders = LaorV4StrategyEngine.generateOrders(
            config = config,
            state = planningState,
            market = LaorV4StrategyMarket(
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
    ): LaorV4Strategy {
        require(status == StrategyExecutionStatus.ACTIVE) { "closed strategy cannot apply fills" }

        val nextState = LaorV4StrategyEngine.applyFills(
            config = config,
            state = state,
            fills = fills.map { it.toLaorFill() },
            closePrice = closePrice,
        )

        return copy(state = nextState)
    }

    private fun LaorV4StrategyState.forOrderGeneration(config: LaorV4StrategyConfig): LaorV4StrategyState {
        return if (mode == LaorV4StrategyMode.NORMAL && t > config.splits - 1) {
            copy(mode = LaorV4StrategyMode.REVERSE, reverseDays = 0)
        } else {
            this
        }
    }

    private fun LaorV4StrategyOrder.toOrderIntent(executionId: StrategyExecutionId): OrderIntent {
        return OrderIntent(
            executionId = executionId,
            side = side.toOrderSide(),
            type = type.toOrderType(),
            price = price,
            quantity = quantity,
            tag = tag.name,
        )
    }

    private fun StrategyExecutionFill.toLaorFill(): LaorV4StrategyFill {
        return LaorV4StrategyFill(
            side = side.toLaorSide(),
            price = price,
            quantity = quantity,
            tag = LaorV4StrategyOrderTag.valueOf(tag),
        )
    }

    private fun LaorV4StrategySide.toOrderSide(): OrderSide {
        return when (this) {
            LaorV4StrategySide.BUY -> OrderSide.BUY
            LaorV4StrategySide.SELL -> OrderSide.SELL
        }
    }

    private fun LaorV4StrategyOrderType.toOrderType(): OrderType {
        return when (this) {
            LaorV4StrategyOrderType.LOC -> OrderType.LOC
            LaorV4StrategyOrderType.MOC -> OrderType.MOC
            LaorV4StrategyOrderType.LIMIT -> OrderType.LIMIT
        }
    }

    private fun OrderSide.toLaorSide(): LaorV4StrategySide {
        return when (this) {
            OrderSide.BUY -> LaorV4StrategySide.BUY
            OrderSide.SELL -> LaorV4StrategySide.SELL
        }
    }
}

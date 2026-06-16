package com.example.backtestservice.application.service

import com.example.backtestservice.domain.backtest.BacktestTrade
import com.example.backtestservice.domain.backtest.BacktestTradeSide
import com.example.backtestservice.domain.market.HistoricalCandle
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyFill
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMarket
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyOrder
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyOrderType
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySide
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import java.math.BigDecimal
import java.time.LocalDate

internal const val LAOR_V4_MARKET_DATA_LOOKBACK_DAYS = 14L
private const val RECENT_CLOSE_COUNT = 5

internal fun LaorV4StrategyState.forOrderGeneration(config: LaorV4StrategyConfig): LaorV4StrategyState {
    return if (mode == LaorV4StrategyMode.NORMAL && progressRound > config.totalSplitCount - 1) {
        copy(mode = LaorV4StrategyMode.REVERSE, reverseModeElapsedDays = 0)
    } else {
        this
    }
}

internal fun List<HistoricalCandle>.toLaorMarket(candle: HistoricalCandle): LaorV4StrategyMarket {
    val previousCandles = takeWhile { it.date < candle.date }
    val previousClose = previousCandles.lastOrNull()?.close?.toDouble() ?: candle.close.toDouble()
    val recentClosePrices = previousCandles.toRecentClosePrices(previousClose)
    return LaorV4StrategyMarket(
        previousClose = previousClose,
        recentClosePrices = recentClosePrices,
    )
}

internal fun List<HistoricalCandle>.toLaorNextSessionMarket(resolvedCandle: HistoricalCandle): LaorV4StrategyMarket {
    val previousCandles = filter { it.date <= resolvedCandle.date }
    val previousClose = resolvedCandle.close.toDouble()
    return LaorV4StrategyMarket(
        previousClose = previousClose,
        recentClosePrices = previousCandles.toRecentClosePrices(previousClose),
    )
}

internal fun LaorV4StrategyOrder.toFilledOrder(candle: HistoricalCandle): LaorV4FilledOrder? {
    val fillPrice = when (type) {
        LaorV4StrategyOrderType.MOC -> candle.close.toDouble()
        LaorV4StrategyOrderType.LOC -> {
            val limitPrice = checkNotNull(price) { "LOC order price is required" }
            when (side) {
                LaorV4StrategySide.BUY -> candle.close.toDouble().takeIf { it <= limitPrice }
                LaorV4StrategySide.SELL -> candle.close.toDouble().takeIf { it >= limitPrice }
            }
        }

        LaorV4StrategyOrderType.LIMIT -> {
            val limitPrice = checkNotNull(price) { "LIMIT order price is required" }
            when (side) {
                LaorV4StrategySide.BUY -> limitPrice.takeIf { candle.low.toDouble() <= limitPrice }
                LaorV4StrategySide.SELL -> limitPrice.takeIf { candle.high.toDouble() >= limitPrice }
            }
        }
    } ?: return null

    return LaorV4FilledOrder(
        order = this,
        fill = LaorV4StrategyFill(
            side = side,
            price = fillPrice,
            quantity = quantity,
            tag = tag,
        ),
    )
}

internal fun LaorV4FilledOrder.toBacktestTrade(
    config: LaorV4StrategyConfig,
    date: LocalDate,
    cycleNo: Int,
): BacktestTrade {
    val executionPrice = config.costPolicy.executionPrice(fill.side, fill.price)
    val tradePrice = executionPrice.toBigDecimalValue()
    val tradeQuantity = fill.quantity.toBigDecimal()
    return BacktestTrade(
        side = when (fill.side) {
            LaorV4StrategySide.BUY -> BacktestTradeSide.BUY
            LaorV4StrategySide.SELL -> BacktestTradeSide.SELL
        },
        symbol = config.symbol.ticker,
        date = date,
        quantity = fill.quantity,
        price = tradePrice,
        notional = tradePrice * tradeQuantity,
        commission = config.costPolicy.tradeCost(fill.side, fill.price, fill.quantity).toBigDecimalValue(),
        orderType = order.type.name,
        orderTag = order.tag.name,
        cycleNo = cycleNo,
    )
}

internal data class LaorV4FilledOrder(
    val order: LaorV4StrategyOrder,
    val fill: LaorV4StrategyFill,
)

internal fun Double.toBigDecimalValue(): BigDecimal {
    return BigDecimal.valueOf(this)
}

private fun List<HistoricalCandle>.toRecentClosePrices(previousClose: Double): List<Double> {
    val prices = takeLast(RECENT_CLOSE_COUNT).map { it.close.toDouble() }
    return if (prices.size >= RECENT_CLOSE_COUNT) {
        prices
    } else {
        List(RECENT_CLOSE_COUNT - prices.size) { previousClose } + prices
    }
}

private operator fun BigDecimal.times(other: BigDecimal): BigDecimal = multiply(other)

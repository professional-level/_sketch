package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.RunBacktestCommand
import com.example.backtestservice.application.port.`in`.RunBacktestUseCase
import com.example.backtestservice.application.port.out.HistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.HistoricalMarketDataPort
import com.example.backtestservice.domain.backtest.BacktestEquityPoint
import com.example.backtestservice.domain.backtest.BacktestResult
import com.example.backtestservice.domain.backtest.BacktestStrategyType
import com.example.backtestservice.domain.backtest.BacktestTrade
import com.example.backtestservice.domain.backtest.BacktestTradeSide
import com.example.backtestservice.domain.market.HistoricalCandle
import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyEngine
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyFill
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMarket
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyOrder
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyOrderType
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySide
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.UUID

@UseCaseImpl
class RunBacktestService(
    private val historicalMarketDataPort: HistoricalMarketDataPort,
) : RunBacktestUseCase {
    override fun execute(command: RunBacktestCommand): BacktestResult {
        command.validate()
        return when (command.strategyType) {
            BacktestStrategyType.BUY_AND_HOLD -> runBuyAndHold(command)
            BacktestStrategyType.LAOR_V4 -> runLaorV4(command)
        }
    }

    private fun runBuyAndHold(command: RunBacktestCommand): BacktestResult {
        val candles = historicalMarketDataPort.findDailyCandles(
            HistoricalDailyCandlesQuery(
                symbol = command.symbol,
                market = command.market,
                from = command.from,
                to = command.to,
            ),
        )
            .sortedBy { it.date }
        require(candles.isNotEmpty()) {
            "historical candles not found: symbol=${command.symbol}, from=${command.from}, to=${command.to}"
        }

        val entry = candles.first()
        val executionPrice = entry.open * (BigDecimal.ONE + command.slippageRate)
        val quantity = command.initialCash.divide(
            executionPrice * (BigDecimal.ONE + command.commissionRate),
            0,
            RoundingMode.DOWN,
        ).toLong()
        val buyNotional = executionPrice * quantity.toBigDecimal()
        val commission = buyNotional * command.commissionRate
        val cashAfterBuy = command.initialCash - buyNotional - commission
        val trades = when {
            quantity > 0 -> listOf(
                BacktestTrade(
                    side = BacktestTradeSide.BUY,
                    symbol = command.symbol.uppercase(),
                    date = entry.date,
                    quantity = quantity,
                    price = executionPrice,
                    notional = buyNotional,
                    commission = commission,
                ),
            )

            else -> emptyList()
        }
        val equityCurve = candles.map { candle ->
            BacktestEquityPoint(
                date = candle.date,
                equity = cashAfterBuy + candle.close * quantity.toBigDecimal(),
                cash = cashAfterBuy,
                positionQuantity = quantity,
                close = candle.close,
            )
        }
        val finalEquity = equityCurve.last().equity
        return BacktestResult(
            runId = UUID.randomUUID(),
            symbol = command.symbol.uppercase(),
            market = command.market.uppercase(),
            strategyType = command.strategyType,
            from = command.from,
            to = command.to,
            initialCash = command.initialCash,
            finalEquity = finalEquity,
            totalReturn = finalEquity.returnFrom(command.initialCash),
            maxDrawdown = equityCurve.maxDrawdown(),
            trades = trades,
            equityCurve = equityCurve,
        )
    }

    private fun runLaorV4(command: RunBacktestCommand): BacktestResult {
        require(command.commissionRate == BigDecimal.ZERO) {
            "LAOR_V4 backtest currently supports zero commissionRate only"
        }
        require(command.slippageRate == BigDecimal.ZERO) {
            "LAOR_V4 backtest currently supports zero slippageRate only"
        }
        val laorV4 = command.laorV4
            ?: throw IllegalArgumentException("laorV4 parameters are required for LAOR_V4 backtest")

        val config = LaorV4StrategyConfig(
            symbol = LaorV4StrategySymbol.valueOf(command.symbol.trim().uppercase()),
            totalSplitCount = laorV4.totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = laorV4.firstBuyLimitPercentAbovePreviousClose,
        )
        val allCandles = historicalMarketDataPort.findDailyCandles(
            HistoricalDailyCandlesQuery(
                symbol = command.symbol,
                market = command.market,
                from = command.from.minusDays(LAOR_V4_MARKET_DATA_LOOKBACK_DAYS),
                to = command.to,
            ),
        )
            .sortedBy { it.date }
        val simulationCandles = allCandles
            .filter { it.date >= command.from && it.date <= command.to }
        require(simulationCandles.isNotEmpty()) {
            "historical candles not found: symbol=${command.symbol}, from=${command.from}, to=${command.to}"
        }

        var state = LaorV4StrategyState(availableCash = command.initialCash.toDouble())
        var cycleNo = 1
        var tradingCompleted = false
        val trades = mutableListOf<BacktestTrade>()
        val equityCurve = mutableListOf<BacktestEquityPoint>()

        simulationCandles.forEach { candle ->
            val currentCycleNo = cycleNo
            val plannedState = state.forOrderGeneration(config)
            val filledOrders = if (tradingCompleted) {
                emptyList()
            } else {
                LaorV4StrategyEngine.generateOrders(
                    config = config,
                    state = plannedState,
                    market = allCandles.toLaorMarket(candle),
                )
                    .mapNotNull { order -> order.toFilledOrder(candle) }
                    .sortedBy { filledOrder -> if (filledOrder.fill.side == LaorV4StrategySide.SELL) 0 else 1 }
            }
            filledOrders.forEach { filledOrder ->
                trades += filledOrder.toBacktestTrade(
                    symbol = config.symbol.ticker,
                    date = candle.date,
                    cycleNo = currentCycleNo,
                )
            }
            val fills = filledOrders.map { it.fill }

            val nextState = if (fills.isEmpty()) {
                plannedState
            } else {
                LaorV4StrategyEngine.applyFills(
                    config = config,
                    state = plannedState,
                    fills = fills,
                    closePrice = candle.close.toDouble(),
                )
            }
            val cycleClosed = state.holdingQuantity > 0 && nextState.holdingQuantity == 0L
            state = nextState
            if (cycleClosed && laorV4.autoRestart) {
                cycleNo += 1
            } else if (cycleClosed) {
                tradingCompleted = true
            }

            equityCurve += BacktestEquityPoint(
                date = candle.date,
                equity = state.availableCash.toBigDecimalValue() +
                    candle.close * state.holdingQuantity.toBigDecimal(),
                cash = state.availableCash.toBigDecimalValue(),
                positionQuantity = state.holdingQuantity,
                close = candle.close,
                averagePurchasePrice = state.averagePurchasePrice.toBigDecimalValue(),
                realizedProfitLoss = state.realizedProfitLoss.toBigDecimalValue(),
                cycleNo = currentCycleNo,
                strategyMode = state.mode.name,
            )
        }

        val finalEquity = equityCurve.last().equity
        return BacktestResult(
            runId = UUID.randomUUID(),
            symbol = config.symbol.ticker,
            market = command.market.uppercase(),
            strategyType = command.strategyType,
            from = command.from,
            to = command.to,
            initialCash = command.initialCash,
            finalEquity = finalEquity,
            totalReturn = finalEquity.returnFrom(command.initialCash),
            maxDrawdown = equityCurve.maxDrawdown(),
            trades = trades,
            equityCurve = equityCurve,
        )
    }

    private fun RunBacktestCommand.validate() {
        require(symbol.isNotBlank()) { "symbol is required" }
        require(!from.isAfter(to)) { "from must be on or before to" }
        require(initialCash > BigDecimal.ZERO) { "initialCash must be positive" }
        require(commissionRate >= BigDecimal.ZERO) { "commissionRate must be zero or positive" }
        require(slippageRate >= BigDecimal.ZERO) { "slippageRate must be zero or positive" }
    }

    private fun List<BacktestEquityPoint>.maxDrawdown(): BigDecimal {
        var peak = BigDecimal.ZERO
        var maxDrawdown = BigDecimal.ZERO
        forEach { point ->
            if (point.equity > peak) peak = point.equity
            if (peak > BigDecimal.ZERO) {
                val drawdown = (peak - point.equity).divide(peak, MathContext.DECIMAL64)
                if (drawdown > maxDrawdown) maxDrawdown = drawdown
            }
        }
        return maxDrawdown
    }

    private fun BigDecimal.returnFrom(initial: BigDecimal): BigDecimal {
        return (this - initial).divide(initial, MathContext.DECIMAL64)
    }

    private fun LaorV4StrategyState.forOrderGeneration(config: LaorV4StrategyConfig): LaorV4StrategyState {
        return if (mode == LaorV4StrategyMode.NORMAL && progressRound > config.totalSplitCount - 1) {
            copy(mode = LaorV4StrategyMode.REVERSE, reverseModeElapsedDays = 0)
        } else {
            this
        }
    }

    private fun List<HistoricalCandle>.toLaorMarket(candle: HistoricalCandle): LaorV4StrategyMarket {
        val previousCandles = takeWhile { it.date < candle.date }
        val previousClose = previousCandles.lastOrNull()?.close?.toDouble() ?: candle.close.toDouble()
        val recentClosePrices = previousCandles
            .takeLast(RECENT_CLOSE_COUNT)
            .map { it.close.toDouble() }
            .let { prices ->
                if (prices.size >= RECENT_CLOSE_COUNT) prices else List(RECENT_CLOSE_COUNT - prices.size) { previousClose } + prices
            }
        return LaorV4StrategyMarket(
            previousClose = previousClose,
            recentClosePrices = recentClosePrices,
        )
    }

    private fun LaorV4StrategyOrder.toFilledOrder(candle: HistoricalCandle): LaorV4FilledOrder? {
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

    private fun LaorV4FilledOrder.toBacktestTrade(
        symbol: String,
        date: java.time.LocalDate,
        cycleNo: Int,
    ): BacktestTrade {
        val tradePrice = fill.price.toBigDecimalValue()
        val tradeQuantity = fill.quantity.toBigDecimal()
        return BacktestTrade(
            side = when (fill.side) {
                LaorV4StrategySide.BUY -> BacktestTradeSide.BUY
                LaorV4StrategySide.SELL -> BacktestTradeSide.SELL
            },
            symbol = symbol,
            date = date,
            quantity = fill.quantity,
            price = tradePrice,
            notional = tradePrice * tradeQuantity,
            commission = BigDecimal.ZERO,
            orderType = order.type.name,
            orderTag = order.tag.name,
            cycleNo = cycleNo,
        )
    }

    private data class LaorV4FilledOrder(
        val order: LaorV4StrategyOrder,
        val fill: LaorV4StrategyFill,
    )

    private fun Double.toBigDecimalValue(): BigDecimal {
        return BigDecimal.valueOf(this)
    }

    private operator fun BigDecimal.plus(other: BigDecimal): BigDecimal = add(other)
    private operator fun BigDecimal.minus(other: BigDecimal): BigDecimal = subtract(other)
    private operator fun BigDecimal.times(other: BigDecimal): BigDecimal = multiply(other)

    companion object {
        private const val LAOR_V4_MARKET_DATA_LOOKBACK_DAYS = 14L
        private const val RECENT_CLOSE_COUNT = 5
    }
}

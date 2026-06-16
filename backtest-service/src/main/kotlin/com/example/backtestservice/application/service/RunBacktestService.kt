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
import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyCostPolicy
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyEngine
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
        val laorV4 = command.laorV4
            ?: throw IllegalArgumentException("laorV4 parameters are required for LAOR_V4 backtest")

        val config = LaorV4StrategyConfig(
            symbol = LaorV4StrategySymbol.valueOf(command.symbol.trim().uppercase()),
            totalSplitCount = laorV4.totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = laorV4.firstBuyLimitPercentAbovePreviousClose,
            costPolicy = LaorV4StrategyCostPolicy(
                commissionRate = command.commissionRate.toDouble(),
                slippageRate = command.slippageRate.toDouble(),
            ),
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
        var dividendIncome = 0.0
        val trades = mutableListOf<BacktestTrade>()
        val equityCurve = mutableListOf<BacktestEquityPoint>()

        simulationCandles.forEach { candle ->
            val currentCycleNo = cycleNo
            val dividendEligibleQuantity = state.holdingQuantity
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
                    config = config,
                    date = candle.date,
                    cycleNo = currentCycleNo,
                )
            }
            val fills = filledOrders.map { it.fill }

            var nextState = if (fills.isEmpty()) {
                plannedState
            } else {
                LaorV4StrategyEngine.applyFills(
                    config = config,
                    state = plannedState,
                    fills = fills,
                    closePrice = candle.close.toDouble(),
                )
            }
            val dailyDividendIncome = candle.dividend.toDouble() * dividendEligibleQuantity
            if (dailyDividendIncome > 0.0) {
                dividendIncome += dailyDividendIncome
                if (laorV4.dividendReinvestment) {
                    nextState = nextState.copy(
                        availableCash = nextState.availableCash + dailyDividendIncome,
                    )
                }
            }
            val cycleClosed = state.holdingQuantity > 0 && nextState.holdingQuantity == 0L
            state = nextState
            if (cycleClosed && laorV4.autoRestart) {
                cycleNo += 1
            } else if (cycleClosed) {
                tradingCompleted = true
            }
            val nonReinvestedDividendIncome = if (laorV4.dividendReinvestment) 0.0 else dividendIncome

            equityCurve += BacktestEquityPoint(
                date = candle.date,
                equity = state.availableCash.toBigDecimalValue() +
                    nonReinvestedDividendIncome.toBigDecimalValue() +
                    candle.close * state.holdingQuantity.toBigDecimal(),
                cash = state.availableCash.toBigDecimalValue() + nonReinvestedDividendIncome.toBigDecimalValue(),
                positionQuantity = state.holdingQuantity,
                close = candle.close,
                averagePurchasePrice = state.averagePurchasePrice.toBigDecimalValue(),
                realizedProfitLoss = state.realizedProfitLoss.toBigDecimalValue(),
                dividendIncome = dividendIncome.toBigDecimalValue(),
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

    private operator fun BigDecimal.plus(other: BigDecimal): BigDecimal = add(other)
    private operator fun BigDecimal.minus(other: BigDecimal): BigDecimal = subtract(other)
    private operator fun BigDecimal.times(other: BigDecimal): BigDecimal = multiply(other)
}

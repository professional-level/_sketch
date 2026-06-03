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

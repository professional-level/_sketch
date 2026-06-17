package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.CalculateLaorV4DashboardCommand
import com.example.backtestservice.application.port.`in`.CalculateLaorV4DashboardUseCase
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataUseCase
import com.example.backtestservice.application.port.`in`.LaorV4DashboardCurrentState
import com.example.backtestservice.application.port.`in`.LaorV4DashboardCycleSummary
import com.example.backtestservice.application.port.`in`.LaorV4DashboardDataCoverage
import com.example.backtestservice.application.port.`in`.LaorV4DashboardNextOrder
import com.example.backtestservice.application.port.`in`.LaorV4DashboardNextOrderContext
import com.example.backtestservice.application.port.`in`.LaorV4DashboardParameters
import com.example.backtestservice.application.port.`in`.LaorV4DashboardResponse
import com.example.backtestservice.application.port.`in`.LaorV4DashboardValuation
import com.example.backtestservice.application.port.out.HistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.HistoricalMarketDataPort
import com.example.backtestservice.application.port.out.MarketCalendarPort
import com.example.backtestservice.application.port.out.MarketTradingDaysQuery
import com.example.backtestservice.domain.market.HistoricalCandle
import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyCostPolicy
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyEngine
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyOrder
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySide
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

@UseCaseImpl
class LaorV4DashboardService(
    private val importHistoricalMarketDataUseCase: ImportHistoricalMarketDataUseCase,
    private val historicalMarketDataPort: HistoricalMarketDataPort,
    private val marketCalendarPort: MarketCalendarPort,
) : CalculateLaorV4DashboardUseCase {
    override fun calculate(command: CalculateLaorV4DashboardCommand): LaorV4DashboardResponse {
        command.validate()

        val symbol = LaorV4StrategySymbol.valueOf(command.symbol.trim().uppercase())
        val market = command.market.trim().uppercase()
        val upperAsOfDate = command.asOfDate ?: LocalDate.now()
        val importFrom = command.startDate.minusDays(LAOR_V4_MARKET_DATA_LOOKBACK_DAYS)
        val config = LaorV4StrategyConfig(
            symbol = symbol,
            totalSplitCount = command.totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = command.firstBuyLimitPercentAbovePreviousClose,
            costPolicy = LaorV4StrategyCostPolicy(
                commissionRate = command.commissionRate.toDouble(),
                slippageRate = command.slippageRate.toDouble(),
            ),
        )

        importMarketData(command, market, importFrom, upperAsOfDate)

        val allCandles = historicalMarketDataPort.findDailyCandles(
            HistoricalDailyCandlesQuery(
                symbol = symbol.ticker,
                market = market,
                from = importFrom,
                to = upperAsOfDate,
            ),
        )
            .distinctBy { it.date }
            .sortedBy { it.date }

        val candlesFromStartReference = allCandles
            .filter { it.date >= command.startDate && it.date <= upperAsOfDate }
            .sortedBy { it.date }
        require(candlesFromStartReference.isNotEmpty()) {
            "historical candles not found: symbol=${symbol.ticker}, from=${command.startDate}, to=$upperAsOfDate"
        }
        require(candlesFromStartReference.first().date == command.startDate) {
            "start reference candle not found: symbol=${symbol.ticker}, date=${command.startDate}"
        }

        val resolvedCandle = candlesFromStartReference.last()
        val resolvedAsOfDate = resolvedCandle.date
        val candlesThroughResolvedAsOf = allCandles.filter { it.date <= resolvedAsOfDate }
        validateHistoricalCoverage(
            symbol = symbol.ticker,
            market = market,
            from = importFrom,
            to = resolvedAsOfDate,
            candles = candlesThroughResolvedAsOf,
        )
        val replay = replay(
            config = config,
            command = command,
            allCandles = candlesThroughResolvedAsOf,
            simulationCandles = candlesFromStartReference.filter {
                it.date > command.startDate && it.date <= resolvedAsOfDate
            },
        )
        val orderSessionDate = nextTradingDay(market, resolvedAsOfDate)
        val nextMarket = candlesThroughResolvedAsOf.toLaorNextSessionMarket(resolvedCandle)
        val nextOrderState = replay.state.forOrderGeneration(config)
        val nextOrders = if (replay.tradingCompleted) {
            emptyList()
        } else {
            LaorV4StrategyEngine.generateOrders(
                config = config,
                state = nextOrderState,
                market = nextMarket,
            )
        }

        val reportCash = replay.reportCash(command.dividendReinvestment)
        val valuation = replay.toValuation(
            config = config,
            close = resolvedCandle.close,
            initialCash = command.initialCash,
            reportCash = reportCash,
        )

        return LaorV4DashboardResponse(
            symbol = symbol.ticker,
            market = market,
            startDate = command.startDate,
            requestedAsOfDate = command.asOfDate,
            resolvedAsOfDate = resolvedAsOfDate,
            parameters = LaorV4DashboardParameters(
                totalSplitCount = command.totalSplitCount,
                firstBuyLimitPercentAbovePreviousClose = command.firstBuyLimitPercentAbovePreviousClose,
                autoRestart = command.autoRestart,
                dividendReinvestment = command.dividendReinvestment,
                autoAdjust = command.autoAdjust,
                commissionRate = command.commissionRate,
                slippageRate = command.slippageRate,
            ),
            current = LaorV4DashboardCurrentState(
                cycleNo = replay.cycleNo,
                mode = replay.state.mode.name,
                progressRound = replay.state.progressRound.toBigDecimalValue(),
                cash = reportCash,
                holdingQuantity = replay.state.holdingQuantity,
                averagePurchasePrice = replay.state.averagePurchasePrice.toBigDecimalValue(),
                realizedProfitLoss = replay.state.realizedProfitLoss.toBigDecimalValue(),
                dividendIncome = replay.dividendIncome.toBigDecimalValue(),
            ),
            valuation = valuation,
            nextOrders = nextOrders.map { it.toDashboardNextOrder() },
            nextOrderContext = nextOrderState.toNextOrderContext(
                config = config,
                market = nextMarket,
                orderSessionDate = orderSessionDate,
                tradingCompleted = replay.tradingCompleted,
            ),
            cycleSummary = LaorV4DashboardCycleSummary(
                cycleCount = replay.cycleNo,
                completedCycleCount = replay.completedCycleCount,
                currentCycleStartedAt = replay.currentCycleStartedAt ?: orderSessionDate.takeIf {
                    command.autoRestart && !replay.tradingCompleted && replay.state.holdingQuantity == 0L
                },
            ),
            dataCoverage = LaorV4DashboardDataCoverage(
                from = candlesThroughResolvedAsOf.first().date,
                to = candlesThroughResolvedAsOf.last().date,
                candleCount = candlesThroughResolvedAsOf.size,
            ),
        )
    }

    private fun CalculateLaorV4DashboardCommand.validate() {
        require(symbol.isNotBlank()) { "symbol is required" }
        require(market.isNotBlank()) { "market is required" }
        require(initialCash > BigDecimal.ZERO) { "initialCash must be positive" }
        require(commissionRate >= BigDecimal.ZERO) { "commissionRate must be zero or positive" }
        require(slippageRate >= BigDecimal.ZERO) { "slippageRate must be zero or positive" }
        require(marketDataTimeoutSeconds > 0) { "marketDataTimeoutSeconds must be positive" }
        val upperAsOfDate = asOfDate ?: LocalDate.now()
        require(!startDate.isAfter(upperAsOfDate)) { "startDate must be on or before asOfDate" }
    }

    private fun importMarketData(
        command: CalculateLaorV4DashboardCommand,
        market: String,
        importFrom: LocalDate,
        upperAsOfDate: LocalDate,
    ) {
        val importCommand = ImportHistoricalMarketDataCommand(
            symbol = command.symbol,
            market = market,
            from = importFrom,
            to = upperAsOfDate,
            autoAdjust = command.autoAdjust,
            timeoutSeconds = command.marketDataTimeoutSeconds,
        )
        runCatching {
            importHistoricalMarketDataUseCase.execute(importCommand)
        }.getOrElse { exception ->
            if (exception is IllegalArgumentException && canResolveLatestCachedCandle(exception, upperAsOfDate)) {
                return
            }
            throw exception
        }
    }

    private fun canResolveLatestCachedCandle(
        exception: IllegalArgumentException,
        upperAsOfDate: LocalDate,
    ): Boolean {
        return !upperAsOfDate.isBefore(LocalDate.now()) &&
            exception.message.orEmpty().contains("historical candles missing for trading days")
    }

    private fun validateHistoricalCoverage(
        symbol: String,
        market: String,
        from: LocalDate,
        to: LocalDate,
        candles: List<HistoricalCandle>,
    ) {
        val candleDates = candles.map { it.date }.toSet()
        val missingTradingDays = marketCalendarPort.findTradingDays(
            MarketTradingDaysQuery(
                market = market,
                from = from,
                to = to,
            ),
        )
            .dates
            .filter { it >= from && it <= to }
            .filter { it !in candleDates }
            .distinct()
            .sorted()
        require(missingTradingDays.isEmpty()) {
            "historical candles missing for resolved replay window: symbol=$symbol, " +
                "from=$from, to=$to, missing=${missingTradingDays.toPreview()}"
        }
    }

    private fun replay(
        config: LaorV4StrategyConfig,
        command: CalculateLaorV4DashboardCommand,
        allCandles: List<HistoricalCandle>,
        simulationCandles: List<HistoricalCandle>,
    ): DashboardReplayResult {
        var state = LaorV4StrategyState(availableCash = command.initialCash.toDouble())
        var cycleNo = 1
        var completedCycleCount = 0
        var tradingCompleted = false
        var dividendIncome = 0.0
        var currentCycleStartedAt: LocalDate? = simulationCandles.firstOrNull()?.date
        var startNextCycleOnNextCandle = false

        simulationCandles.forEach { candle ->
            if (startNextCycleOnNextCandle) {
                currentCycleStartedAt = candle.date
                startNextCycleOnNextCandle = false
            }

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
                if (command.dividendReinvestment) {
                    nextState = nextState.copy(
                        availableCash = nextState.availableCash + dailyDividendIncome,
                    )
                }
            }

            val cycleClosed = state.holdingQuantity > 0 && nextState.holdingQuantity == 0L
            state = nextState
            if (cycleClosed) {
                completedCycleCount += 1
                if (command.autoRestart) {
                    cycleNo += 1
                    currentCycleStartedAt = null
                    startNextCycleOnNextCandle = true
                } else {
                    tradingCompleted = true
                    currentCycleStartedAt = null
                }
            }
        }

        return DashboardReplayResult(
            state = state,
            cycleNo = cycleNo,
            completedCycleCount = completedCycleCount,
            currentCycleStartedAt = currentCycleStartedAt,
            tradingCompleted = tradingCompleted,
            dividendIncome = dividendIncome,
        )
    }

    private fun nextTradingDay(market: String, resolvedAsOfDate: LocalDate): LocalDate {
        val from = resolvedAsOfDate.plusDays(1)
        val to = resolvedAsOfDate.plusDays(NEXT_TRADING_DAY_LOOKAHEAD_DAYS)
        return marketCalendarPort.findTradingDays(
            MarketTradingDaysQuery(
                market = market,
                from = from,
                to = to,
            ),
        )
            .dates
            .filter { it > resolvedAsOfDate }
            .minOrNull()
            ?: throw IllegalArgumentException("next trading day not found: market=$market, from=$from, to=$to")
    }

    private fun DashboardReplayResult.reportCash(dividendReinvestment: Boolean): BigDecimal {
        val nonReinvestedDividendIncome = if (dividendReinvestment) 0.0 else dividendIncome
        return (state.availableCash + nonReinvestedDividendIncome).toBigDecimalValue()
    }

    private fun DashboardReplayResult.toValuation(
        config: LaorV4StrategyConfig,
        close: BigDecimal,
        initialCash: BigDecimal,
        reportCash: BigDecimal,
    ): LaorV4DashboardValuation {
        val holdingQuantity = state.holdingQuantity
        val grossPositionMarketValue = close * holdingQuantity.toBigDecimal()
        val netPositionMarketValue = if (holdingQuantity == 0L) {
            BigDecimal.ZERO
        } else {
            config.costPolicy.sellCashProceeds(close.toDouble(), holdingQuantity).toBigDecimalValue()
        }
        val grossEquity = reportCash + grossPositionMarketValue
        val netEquity = reportCash + netPositionMarketValue
        val totalProfitLoss = netEquity - initialCash
        val positionCost = state.averagePurchasePrice.toBigDecimalValue() * holdingQuantity.toBigDecimal()
        val positionUnrealizedProfitLoss = netPositionMarketValue - positionCost
        return LaorV4DashboardValuation(
            close = close,
            positionMarketValue = grossPositionMarketValue,
            grossEquity = grossEquity,
            netEquity = netEquity,
            totalProfitLoss = totalProfitLoss,
            totalReturnPercent = totalProfitLoss.percentOf(initialCash),
            positionUnrealizedProfitLoss = positionUnrealizedProfitLoss,
            positionReturnPercent = positionUnrealizedProfitLoss.percentOf(positionCost),
        )
    }

    private fun LaorV4StrategyOrder.toDashboardNextOrder(): LaorV4DashboardNextOrder {
        val orderPrice = price?.toBigDecimalValue()
        return LaorV4DashboardNextOrder(
            side = side.name,
            orderType = type.name,
            price = orderPrice,
            quantity = quantity,
            orderTag = tag.name,
            notional = orderPrice?.let { it * quantity.toBigDecimal() },
        )
    }

    private fun LaorV4StrategyState.toNextOrderContext(
        config: LaorV4StrategyConfig,
        market: com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMarket,
        orderSessionDate: LocalDate,
        tradingCompleted: Boolean,
    ): LaorV4DashboardNextOrderContext {
        val normalHoldingState = !tradingCompleted && mode == LaorV4StrategyMode.NORMAL && holdingQuantity > 0L
        val starPercent = if (normalHoldingState) {
            LaorV4StrategyEngine.normalModeStarProfitPercent(config, this)
        } else {
            null
        }
        val starPrice = if (normalHoldingState) {
            LaorV4StrategyEngine.normalModeStarPrice(config, this)
        } else {
            null
        }
        val targetSellPrice = if (normalHoldingState) {
            LaorV4StrategyEngine.targetSellPrice(config, this)
        } else {
            null
        }
        val reverseStarPrice = if (!tradingCompleted && mode == LaorV4StrategyMode.REVERSE) {
            LaorV4StrategyEngine.reverseModeStarPrice(market)
        } else {
            null
        }
        val oneBuyBudget = when {
            tradingCompleted -> 0.0
            mode == LaorV4StrategyMode.REVERSE -> availableCash * REVERSE_BUY_AVAILABLE_CASH_RATIO
            holdingQuantity == 0L -> availableCash / config.totalSplitCount
            else -> LaorV4StrategyEngine.singleBuyBudget(config, this)
        }
        return LaorV4DashboardNextOrderContext(
            orderSessionDate = orderSessionDate,
            previousClose = market.previousClose.toBigDecimalValue(),
            starPercent = starPercent?.toBigDecimalValue(),
            starPrice = starPrice?.toBigDecimalValue(),
            starBuyPrice = starPrice?.minus(STAR_BUY_PRICE_OFFSET)?.toBigDecimalValue(),
            targetSellPrice = targetSellPrice?.toBigDecimalValue(),
            oneBuyBudget = oneBuyBudget.toBigDecimalValue(),
            reverseStarPrice = reverseStarPrice?.toBigDecimalValue(),
        )
    }

    private fun BigDecimal.percentOf(base: BigDecimal): BigDecimal {
        return if (base.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO
        } else {
            divide(base, MathContext.DECIMAL64) * PERCENT_MULTIPLIER
        }
    }

    private fun List<LocalDate>.toPreview(): String {
        val suffix = if (size > MAX_MISSING_DATES_IN_ERROR) ", ... total=$size" else ""
        return take(MAX_MISSING_DATES_IN_ERROR).joinToString(prefix = "[", postfix = "$suffix]")
    }

    private data class DashboardReplayResult(
        val state: LaorV4StrategyState,
        val cycleNo: Int,
        val completedCycleCount: Int,
        val currentCycleStartedAt: LocalDate?,
        val tradingCompleted: Boolean,
        val dividendIncome: Double,
    )

    private operator fun BigDecimal.plus(other: BigDecimal): BigDecimal = add(other)
    private operator fun BigDecimal.minus(other: BigDecimal): BigDecimal = subtract(other)
    private operator fun BigDecimal.times(other: BigDecimal): BigDecimal = multiply(other)

    companion object {
        private const val NEXT_TRADING_DAY_LOOKAHEAD_DAYS = 21L
        private const val REVERSE_BUY_AVAILABLE_CASH_RATIO = 0.25
        private const val STAR_BUY_PRICE_OFFSET = 0.01
        private const val MAX_MISSING_DATES_IN_ERROR = 10
        private val PERCENT_MULTIPLIER = BigDecimal("100")
    }
}

package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.CalculateLaorV4DashboardCommand
import com.example.backtestservice.application.port.`in`.CalculateLaorV4DashboardUseCase
import com.example.backtestservice.application.port.`in`.FindLaorV4DashboardDailyFlowQuery
import com.example.backtestservice.application.port.`in`.FindLaorV4DashboardDetailsUseCase
import com.example.backtestservice.application.port.`in`.FindLaorV4DashboardIndexQuery
import com.example.backtestservice.application.port.`in`.FindLaorV4DashboardTradesQuery
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataUseCase
import com.example.backtestservice.application.port.`in`.LaorV4DashboardChartPoint
import com.example.backtestservice.application.port.`in`.LaorV4DashboardChartSeries
import com.example.backtestservice.application.port.`in`.LaorV4DashboardCurrentState
import com.example.backtestservice.application.port.`in`.LaorV4DashboardCycleSummary
import com.example.backtestservice.application.port.`in`.LaorV4DashboardDailyFlowItem
import com.example.backtestservice.application.port.`in`.LaorV4DashboardDailyFlowPage
import com.example.backtestservice.application.port.`in`.LaorV4DashboardDailyState
import com.example.backtestservice.application.port.`in`.LaorV4DashboardDataCoverage
import com.example.backtestservice.application.port.`in`.LaorV4DashboardDetailSort
import com.example.backtestservice.application.port.`in`.LaorV4DashboardIndexBase
import com.example.backtestservice.application.port.`in`.LaorV4DashboardIndexPoint
import com.example.backtestservice.application.port.`in`.LaorV4DashboardIndexResponse
import com.example.backtestservice.application.port.`in`.LaorV4DashboardNextOrder
import com.example.backtestservice.application.port.`in`.LaorV4DashboardNextOrderContext
import com.example.backtestservice.application.port.`in`.LaorV4DashboardParameters
import com.example.backtestservice.application.port.`in`.LaorV4DashboardResponse
import com.example.backtestservice.application.port.`in`.LaorV4DashboardTradesPage
import com.example.backtestservice.application.port.`in`.LaorV4DashboardValuation
import com.example.backtestservice.application.port.out.HistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.HistoricalMarketDataPort
import com.example.backtestservice.application.port.out.MarketCalendarPort
import com.example.backtestservice.application.port.out.MarketTradingDaysQuery
import com.example.backtestservice.domain.backtest.BacktestTrade
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
) : CalculateLaorV4DashboardUseCase,
    FindLaorV4DashboardDetailsUseCase {
    override fun calculate(command: CalculateLaorV4DashboardCommand): LaorV4DashboardResponse {
        return calculateInternal(command).response
    }

    override fun findTrades(query: FindLaorV4DashboardTradesQuery): LaorV4DashboardTradesPage {
        query.validatePage()
        val calculation = calculateInternal(query.command)
        val trades = calculation.replay.trades.sortedBy(query.sort) { it.date }
        return LaorV4DashboardTradesPage(
            symbol = calculation.response.symbol,
            market = calculation.response.market,
            startDate = calculation.response.startDate,
            requestedAsOfDate = calculation.response.requestedAsOfDate,
            resolvedAsOfDate = calculation.response.resolvedAsOfDate,
            page = query.page,
            size = query.size,
            total = trades.size,
            items = trades.page(query.page, query.size),
        )
    }

    override fun findDailyFlow(query: FindLaorV4DashboardDailyFlowQuery): LaorV4DashboardDailyFlowPage {
        query.validatePage()
        val calculation = calculateInternal(query.command)
        val flows = calculation.replay.dailyFlows.sortedBy(query.sort) { it.date }
        return LaorV4DashboardDailyFlowPage(
            symbol = calculation.response.symbol,
            market = calculation.response.market,
            startDate = calculation.response.startDate,
            requestedAsOfDate = calculation.response.requestedAsOfDate,
            resolvedAsOfDate = calculation.response.resolvedAsOfDate,
            page = query.page,
            size = query.size,
            total = flows.size,
            items = flows.page(query.page, query.size),
        )
    }

    override fun findIndex(query: FindLaorV4DashboardIndexQuery): LaorV4DashboardIndexResponse {
        val calculation = calculateInternal(query.command)
        val points = calculation.toIndexPoints()
        return LaorV4DashboardIndexResponse(
            symbol = calculation.response.symbol,
            market = calculation.response.market,
            startDate = calculation.response.startDate,
            requestedAsOfDate = calculation.response.requestedAsOfDate,
            resolvedAsOfDate = calculation.response.resolvedAsOfDate,
            base = LaorV4DashboardIndexBase(
                baseDate = calculation.response.startDate,
                baseValue = LAOR_INDEX_BASE_VALUE,
                initialCash = calculation.initialCash,
                benchmarkSymbol = calculation.response.symbol,
                benchmarkClose = calculation.baseCandle.close,
            ),
            series = points.toChartSeries(calculation.response.symbol),
            points = points,
        )
    }

    private fun calculateInternal(command: CalculateLaorV4DashboardCommand): DashboardCalculation {
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

        val response = LaorV4DashboardResponse(
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
        return DashboardCalculation(
            response = response,
            replay = replay,
            config = config,
            baseCandle = candlesFromStartReference.first(),
            initialCash = command.initialCash,
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

    private fun FindLaorV4DashboardTradesQuery.validatePage() {
        validatePage(page, size)
    }

    private fun FindLaorV4DashboardDailyFlowQuery.validatePage() {
        validatePage(page, size)
    }

    private fun validatePage(page: Int, size: Int) {
        require(page >= 0) { "page must be zero or positive" }
        require(size in 1..1000) { "size must be between 1 and 1000" }
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
        val trades = mutableListOf<BacktestTrade>()
        val dailyFlows = mutableListOf<LaorV4DashboardDailyFlowItem>()

        simulationCandles.forEach { candle ->
            if (startNextCycleOnNextCandle) {
                currentCycleStartedAt = candle.date
                startNextCycleOnNextCandle = false
            }

            val cycleNoBefore = cycleNo
            val stateBefore = state.toDailyState(cycleNoBefore, dividendIncome, command.dividendReinvestment)
            val dividendEligibleQuantity = state.holdingQuantity
            val plannedState = state.forOrderGeneration(config)
            val market = allCandles.toLaorMarket(candle)
            val generatedOrders = if (tradingCompleted) {
                emptyList()
            } else {
                LaorV4StrategyEngine.generateOrders(
                    config = config,
                    state = plannedState,
                    market = market,
                )
            }
            val filledOrders = generatedOrders
                .mapNotNull { order -> order.toFilledOrder(candle) }
                .sortedBy { filledOrder -> if (filledOrder.fill.side == LaorV4StrategySide.SELL) 0 else 1 }
            val fills = filledOrders.map { it.fill }
            val dailyTrades = filledOrders.map { filledOrder ->
                filledOrder.toBacktestTrade(
                    config = config,
                    date = candle.date,
                    cycleNo = cycleNoBefore,
                )
            }
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
            trades += dailyTrades
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
            val referenceDate = allCandles.lastOrNull { it.date < candle.date }?.date ?: candle.date
            dailyFlows += LaorV4DashboardDailyFlowItem(
                date = candle.date,
                referenceDate = referenceDate,
                cycleNo = cycleNoBefore,
                previousClose = market.previousClose.toBigDecimalValue(),
                close = candle.close,
                orders = generatedOrders.map { it.toDashboardNextOrder() },
                filledOrders = dailyTrades,
                before = stateBefore,
                after = state.toDailyState(cycleNo, dividendIncome, command.dividendReinvestment),
                dailyDividendIncome = dailyDividendIncome.toBigDecimalValue(),
                cycleClosed = cycleClosed,
                tradingCompleted = tradingCompleted,
            )
        }

        return DashboardReplayResult(
            state = state,
            cycleNo = cycleNo,
            completedCycleCount = completedCycleCount,
            currentCycleStartedAt = currentCycleStartedAt,
            tradingCompleted = tradingCompleted,
            dividendIncome = dividendIncome,
            trades = trades,
            dailyFlows = dailyFlows,
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

    private fun DashboardCalculation.toIndexPoints(): List<LaorV4DashboardIndexPoint> {
        val basePoint = LaorV4DashboardIndexPoint(
            date = response.startDate,
            laorIndex = LAOR_INDEX_BASE_VALUE,
            benchmarkIndex = LAOR_INDEX_BASE_VALUE,
            netEquity = initialCash,
            cash = initialCash,
            holdingQuantity = 0,
            averagePurchasePrice = BigDecimal.ZERO,
            realizedProfitLoss = BigDecimal.ZERO,
            dividendIncome = BigDecimal.ZERO,
            progressRound = BigDecimal.ZERO,
            cycleNo = 1,
            mode = LaorV4StrategyMode.NORMAL.name,
            close = baseCandle.close,
            benchmarkClose = baseCandle.close,
            basePoint = true,
        )
        val replayPoints = replay.dailyFlows.map { flow ->
            val netEquity = flow.after.toNetEquity(config, flow.close)
            LaorV4DashboardIndexPoint(
                date = flow.date,
                laorIndex = netEquity.indexFrom(initialCash),
                benchmarkIndex = flow.close.indexFrom(baseCandle.close),
                netEquity = netEquity,
                cash = flow.after.cash,
                holdingQuantity = flow.after.holdingQuantity,
                averagePurchasePrice = flow.after.averagePurchasePrice,
                realizedProfitLoss = flow.after.realizedProfitLoss,
                dividendIncome = flow.after.dividendIncome,
                progressRound = flow.after.progressRound,
                cycleNo = flow.after.cycleNo,
                mode = flow.after.mode,
                close = flow.close,
                benchmarkClose = flow.close,
                basePoint = false,
            )
        }
        return (listOf(basePoint) + replayPoints)
            .distinctBy { it.date }
            .sortedBy { it.date }
    }

    private fun LaorV4DashboardDailyState.toNetEquity(
        config: LaorV4StrategyConfig,
        close: BigDecimal,
    ): BigDecimal {
        val netPositionMarketValue = if (holdingQuantity == 0L) {
            BigDecimal.ZERO
        } else {
            config.costPolicy.sellCashProceeds(close.toDouble(), holdingQuantity).toBigDecimalValue()
        }
        return cash + netPositionMarketValue
    }

    private fun BigDecimal.indexFrom(base: BigDecimal): BigDecimal {
        return if (base.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO
        } else {
            divide(base, MathContext.DECIMAL64) * LAOR_INDEX_BASE_VALUE
        }
    }

    private fun List<LaorV4DashboardIndexPoint>.toChartSeries(symbol: String): List<LaorV4DashboardChartSeries> {
        return listOf(
            LaorV4DashboardChartSeries(
                key = "laorIndex",
                label = "LAOR $symbol",
                chartType = LIGHTWEIGHT_CHART_LINE_SERIES,
                color = LAOR_INDEX_COLOR,
                data = map { point ->
                    LaorV4DashboardChartPoint(
                        time = point.date,
                        value = point.laorIndex,
                    )
                },
            ),
            LaorV4DashboardChartSeries(
                key = "benchmarkIndex",
                label = "$symbol Buy & Hold",
                chartType = LIGHTWEIGHT_CHART_LINE_SERIES,
                color = BENCHMARK_INDEX_COLOR,
                data = map { point ->
                    LaorV4DashboardChartPoint(
                        time = point.date,
                        value = point.benchmarkIndex,
                    )
                },
            ),
        )
    }

    private fun LaorV4StrategyState.toDailyState(
        cycleNo: Int,
        dividendIncome: Double,
        dividendReinvestment: Boolean,
    ): LaorV4DashboardDailyState {
        val nonReinvestedDividendIncome = if (dividendReinvestment) 0.0 else dividendIncome
        return LaorV4DashboardDailyState(
            cycleNo = cycleNo,
            mode = mode.name,
            progressRound = progressRound.toBigDecimalValue(),
            cash = (availableCash + nonReinvestedDividendIncome).toBigDecimalValue(),
            holdingQuantity = holdingQuantity,
            averagePurchasePrice = averagePurchasePrice.toBigDecimalValue(),
            realizedProfitLoss = realizedProfitLoss.toBigDecimalValue(),
            dividendIncome = dividendIncome.toBigDecimalValue(),
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

    private inline fun <T, R : Comparable<R>> List<T>.sortedBy(
        sort: LaorV4DashboardDetailSort,
        crossinline selector: (T) -> R,
    ): List<T> {
        val sorted = sortedBy(selector)
        return when (sort) {
            LaorV4DashboardDetailSort.ASC -> sorted
            LaorV4DashboardDetailSort.DESC -> sorted.asReversed()
        }
    }

    private fun <T> List<T>.page(page: Int, size: Int): List<T> {
        val offset = page * size
        return drop(offset).take(size)
    }

    private data class DashboardCalculation(
        val response: LaorV4DashboardResponse,
        val replay: DashboardReplayResult,
        val config: LaorV4StrategyConfig,
        val baseCandle: HistoricalCandle,
        val initialCash: BigDecimal,
    )

    private data class DashboardReplayResult(
        val state: LaorV4StrategyState,
        val cycleNo: Int,
        val completedCycleCount: Int,
        val currentCycleStartedAt: LocalDate?,
        val tradingCompleted: Boolean,
        val dividendIncome: Double,
        val trades: List<BacktestTrade>,
        val dailyFlows: List<LaorV4DashboardDailyFlowItem>,
    )

    private operator fun BigDecimal.plus(other: BigDecimal): BigDecimal = add(other)
    private operator fun BigDecimal.minus(other: BigDecimal): BigDecimal = subtract(other)
    private operator fun BigDecimal.times(other: BigDecimal): BigDecimal = multiply(other)

    companion object {
        private const val NEXT_TRADING_DAY_LOOKAHEAD_DAYS = 21L
        private const val REVERSE_BUY_AVAILABLE_CASH_RATIO = 0.25
        private const val STAR_BUY_PRICE_OFFSET = 0.01
        private const val MAX_MISSING_DATES_IN_ERROR = 10
        private const val LIGHTWEIGHT_CHART_LINE_SERIES = "LineSeries"
        private const val LAOR_INDEX_COLOR = "#0f766e"
        private const val BENCHMARK_INDEX_COLOR = "#64748b"
        private val PERCENT_MULTIPLIER = BigDecimal("100")
        private val LAOR_INDEX_BASE_VALUE = BigDecimal("100")
    }
}

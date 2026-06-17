package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.CalculateLaorV4DashboardCommand
import com.example.backtestservice.application.port.`in`.CalculateLaorV4DashboardUseCase
import com.example.backtestservice.application.port.`in`.CalculateLaorV4PortfolioDashboardQuery
import com.example.backtestservice.application.port.`in`.CreateLaorV4PortfolioCommand
import com.example.backtestservice.application.port.`in`.FindLaorV4DashboardIndexQuery
import com.example.backtestservice.application.port.`in`.FindLaorV4DashboardDailyFlowQuery
import com.example.backtestservice.application.port.`in`.FindLaorV4DashboardDetailsUseCase
import com.example.backtestservice.application.port.`in`.FindLaorV4DashboardTradesQuery
import com.example.backtestservice.application.port.`in`.FindLaorV4PortfolioDailyFlowQuery
import com.example.backtestservice.application.port.`in`.FindLaorV4PortfolioIndexQuery
import com.example.backtestservice.application.port.`in`.FindLaorV4PortfolioTradesQuery
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
import com.example.backtestservice.application.port.`in`.LaorV4DashboardNextOrderContext
import com.example.backtestservice.application.port.`in`.LaorV4DashboardParameters
import com.example.backtestservice.application.port.`in`.LaorV4DashboardResponse
import com.example.backtestservice.application.port.`in`.LaorV4DashboardTradesPage
import com.example.backtestservice.application.port.`in`.LaorV4DashboardValuation
import com.example.backtestservice.application.port.out.LaorV4PortfolioStorePort
import com.example.backtestservice.domain.backtest.BacktestTrade
import com.example.backtestservice.domain.backtest.BacktestTradeSide
import com.example.backtestservice.domain.backtest.LaorV4PortfolioRecord
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class LaorV4PortfolioServiceTest {
    @Test
    fun `creates and lists stored Laor V4 portfolios`() {
        val store = FakeLaorV4PortfolioStorePort()
        val service = LaorV4PortfolioService(
            store,
            FakeLaorV4DashboardUseCase(),
            FakeLaorV4DashboardUseCase(),
        )

        val created = service.create(
            CreateLaorV4PortfolioCommand(
                name = "Main TQQQ",
                symbol = "tqqq",
                startDate = LocalDate.parse("2024-01-02"),
                initialCash = "10000".toBigDecimal(),
                totalSplitCount = 30,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
            ),
        )

        assertEquals("Main TQQQ", created.name)
        assertEquals("TQQQ", created.symbol)
        assertEquals("US", created.market)
        assertEquals(30, created.totalSplitCount)
        assertEquals(null, created.latestSnapshot)
        assertEquals(created.portfolioId, service.find(created.portfolioId).portfolioId)
        assertEquals(listOf(created.portfolioId), service.findAll().map { it.portfolioId })
    }

    @Test
    fun `calculates dashboard from stored settings and saves latest snapshot`() {
        val store = FakeLaorV4PortfolioStorePort()
        val dashboard = FakeLaorV4DashboardUseCase()
        val service = LaorV4PortfolioService(store, dashboard, dashboard)
        val created = service.create(
            CreateLaorV4PortfolioCommand(
                symbol = "SOXL",
                market = "us",
                startDate = LocalDate.parse("2024-01-02"),
                initialCash = "20000".toBigDecimal(),
                totalSplitCount = 40,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
                autoRestart = false,
                dividendReinvestment = true,
                autoAdjust = true,
                commissionRate = "0.001".toBigDecimal(),
                slippageRate = "0.002".toBigDecimal(),
            ),
        )

        val response = service.calculate(
            CalculateLaorV4PortfolioDashboardQuery(
                portfolioId = created.portfolioId,
                asOfDate = LocalDate.parse("2024-01-05"),
                marketDataTimeoutSeconds = 45,
            ),
        )

        val command = dashboard.commands.single()
        assertEquals("SOXL", command.symbol)
        assertEquals("US", command.market)
        assertEquals(LocalDate.parse("2024-01-02"), command.startDate)
        assertEquals(LocalDate.parse("2024-01-05"), command.asOfDate)
        assertEquals("20000".toBigDecimal(), command.initialCash)
        assertEquals(40, command.totalSplitCount)
        assertEquals(false, command.autoRestart)
        assertEquals(true, command.dividendReinvestment)
        assertEquals(true, command.autoAdjust)
        assertEquals("0.001".toBigDecimal(), command.commissionRate)
        assertEquals("0.002".toBigDecimal(), command.slippageRate)
        assertEquals(45, command.marketDataTimeoutSeconds)

        assertEquals(created.portfolioId, response.portfolio.portfolioId)
        val snapshot = assertNotNull(response.portfolio.latestSnapshot)
        assertEquals(LocalDate.parse("2024-01-05"), snapshot.resolvedAsOfDate)
        assertEquals(2, snapshot.cycleNo)
        assertEquals("NORMAL", snapshot.mode)
        assertEquals(5, snapshot.holdingQuantity)
        assertNotNull(store.findById(created.portfolioId)?.latestSnapshot)
    }

    @Test
    fun `rejects invalid portfolio profile`() {
        val service = LaorV4PortfolioService(
            FakeLaorV4PortfolioStorePort(),
            FakeLaorV4DashboardUseCase(),
            FakeLaorV4DashboardUseCase(),
        )

        assertFailsWith<IllegalArgumentException> {
            service.create(
                CreateLaorV4PortfolioCommand(
                    symbol = "TQQQ",
                    startDate = LocalDate.parse("2024-01-02"),
                    initialCash = "10000".toBigDecimal(),
                    totalSplitCount = 10,
                    firstBuyLimitPercentAbovePreviousClose = 12.0,
                ),
            )
        }
    }

    @Test
    fun `finds paged trades from stored settings`() {
        val store = FakeLaorV4PortfolioStorePort()
        val dashboard = FakeLaorV4DashboardUseCase()
        val service = LaorV4PortfolioService(store, dashboard, dashboard)
        val created = service.create(
            CreateLaorV4PortfolioCommand(
                symbol = "TQQQ",
                startDate = LocalDate.parse("2024-01-02"),
                initialCash = "10000".toBigDecimal(),
                totalSplitCount = 20,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
            ),
        )

        val response = service.findTrades(
            FindLaorV4PortfolioTradesQuery(
                portfolioId = created.portfolioId,
                asOfDate = LocalDate.parse("2024-01-05"),
                page = 1,
                size = 1,
                sort = LaorV4DashboardDetailSort.DESC,
                marketDataTimeoutSeconds = 45,
            ),
        )

        val query = dashboard.tradeQueries.single()
        assertEquals(LocalDate.parse("2024-01-05"), query.command.asOfDate)
        assertEquals(1, query.page)
        assertEquals(1, query.size)
        assertEquals(LaorV4DashboardDetailSort.DESC, query.sort)
        assertEquals(45, query.command.marketDataTimeoutSeconds)
        assertEquals(created.portfolioId, response.portfolioId)
        assertEquals(2, response.total)
        assertEquals(1, response.items.size)
    }

    @Test
    fun `finds paged daily flow from stored settings`() {
        val store = FakeLaorV4PortfolioStorePort()
        val dashboard = FakeLaorV4DashboardUseCase()
        val service = LaorV4PortfolioService(store, dashboard, dashboard)
        val created = service.create(
            CreateLaorV4PortfolioCommand(
                symbol = "TQQQ",
                startDate = LocalDate.parse("2024-01-02"),
                initialCash = "10000".toBigDecimal(),
                totalSplitCount = 20,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
            ),
        )

        val response = service.findDailyFlow(
            FindLaorV4PortfolioDailyFlowQuery(
                portfolioId = created.portfolioId,
                asOfDate = LocalDate.parse("2024-01-05"),
                page = 0,
                size = 10,
            ),
        )

        val query = dashboard.dailyFlowQueries.single()
        assertEquals(LocalDate.parse("2024-01-05"), query.command.asOfDate)
        assertEquals(created.portfolioId, response.portfolioId)
        assertEquals(1, response.total)
        assertEquals(LocalDate.parse("2024-01-03"), response.items.single().date)
    }

    @Test
    fun `finds LAOR index from stored settings`() {
        val store = FakeLaorV4PortfolioStorePort()
        val dashboard = FakeLaorV4DashboardUseCase()
        val service = LaorV4PortfolioService(store, dashboard, dashboard)
        val created = service.create(
            CreateLaorV4PortfolioCommand(
                symbol = "TQQQ",
                startDate = LocalDate.parse("2024-01-02"),
                initialCash = "10000".toBigDecimal(),
                totalSplitCount = 20,
                firstBuyLimitPercentAbovePreviousClose = 12.0,
            ),
        )

        val response = service.findIndex(
            FindLaorV4PortfolioIndexQuery(
                portfolioId = created.portfolioId,
                asOfDate = LocalDate.parse("2024-01-05"),
                marketDataTimeoutSeconds = 45,
            ),
        )

        val query = dashboard.indexQueries.single()
        assertEquals(LocalDate.parse("2024-01-05"), query.command.asOfDate)
        assertEquals(45, query.command.marketDataTimeoutSeconds)
        assertEquals(created.portfolioId, response.portfolioId)
        assertEquals("laorIndex", response.series.single().key)
        assertEquals(LocalDate.parse("2024-01-02"), response.series.single().data.single().time)
    }

    private class FakeLaorV4PortfolioStorePort : LaorV4PortfolioStorePort {
        private val records = linkedMapOf<UUID, LaorV4PortfolioRecord>()

        override fun save(record: LaorV4PortfolioRecord) {
            records[record.portfolioId] = record
        }

        override fun findById(portfolioId: UUID): LaorV4PortfolioRecord? {
            return records[portfolioId]
        }

        override fun findAll(): List<LaorV4PortfolioRecord> {
            return records.values.toList()
        }
    }

    private class FakeLaorV4DashboardUseCase : CalculateLaorV4DashboardUseCase, FindLaorV4DashboardDetailsUseCase {
        val commands = mutableListOf<CalculateLaorV4DashboardCommand>()
        val tradeQueries = mutableListOf<FindLaorV4DashboardTradesQuery>()
        val dailyFlowQueries = mutableListOf<FindLaorV4DashboardDailyFlowQuery>()
        val indexQueries = mutableListOf<FindLaorV4DashboardIndexQuery>()

        override fun calculate(command: CalculateLaorV4DashboardCommand): LaorV4DashboardResponse {
            commands += command
            val resolvedAsOfDate = command.asOfDate ?: LocalDate.parse("2024-01-05")
            return LaorV4DashboardResponse(
                symbol = command.symbol,
                market = command.market,
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
                    cycleNo = 2,
                    mode = "NORMAL",
                    progressRound = "1.5".toBigDecimal(),
                    cash = "15000".toBigDecimal(),
                    holdingQuantity = 5,
                    averagePurchasePrice = "100".toBigDecimal(),
                    realizedProfitLoss = "12.5".toBigDecimal(),
                    dividendIncome = "1.5".toBigDecimal(),
                ),
                valuation = LaorV4DashboardValuation(
                    close = "110".toBigDecimal(),
                    positionMarketValue = "550".toBigDecimal(),
                    grossEquity = "15550".toBigDecimal(),
                    netEquity = "15549".toBigDecimal(),
                    totalProfitLoss = "549".toBigDecimal(),
                    totalReturnPercent = "2.745".toBigDecimal(),
                    positionUnrealizedProfitLoss = "49".toBigDecimal(),
                    positionReturnPercent = "9.8".toBigDecimal(),
                ),
                nextOrders = emptyList(),
                nextOrderContext = LaorV4DashboardNextOrderContext(
                    orderSessionDate = resolvedAsOfDate.plusDays(1),
                    previousClose = "110".toBigDecimal(),
                    starPercent = null,
                    starPrice = null,
                    starBuyPrice = null,
                    targetSellPrice = null,
                    oneBuyBudget = "1000".toBigDecimal(),
                    reverseStarPrice = null,
                ),
                cycleSummary = LaorV4DashboardCycleSummary(
                    cycleCount = 2,
                    completedCycleCount = 1,
                    currentCycleStartedAt = command.startDate,
                ),
                dataCoverage = LaorV4DashboardDataCoverage(
                    from = command.startDate.minusDays(14),
                    to = resolvedAsOfDate,
                    candleCount = 10,
                ),
            )
        }

        override fun findTrades(query: FindLaorV4DashboardTradesQuery): LaorV4DashboardTradesPage {
            tradeQueries += query
            val resolvedAsOfDate = query.command.asOfDate ?: LocalDate.parse("2024-01-05")
            return LaorV4DashboardTradesPage(
                symbol = query.command.symbol,
                market = query.command.market,
                startDate = query.command.startDate,
                requestedAsOfDate = query.command.asOfDate,
                resolvedAsOfDate = resolvedAsOfDate,
                page = query.page,
                size = query.size,
                total = 2,
                items = listOf(
                    BacktestTrade(
                        side = BacktestTradeSide.BUY,
                        symbol = query.command.symbol,
                        date = LocalDate.parse("2024-01-03"),
                        quantity = 1,
                        price = "100".toBigDecimal(),
                        notional = "100".toBigDecimal(),
                        commission = "0.05".toBigDecimal(),
                        orderType = "LOC",
                        orderTag = "FIRST_BUY",
                        cycleNo = 1,
                    ),
                ),
            )
        }

        override fun findDailyFlow(query: FindLaorV4DashboardDailyFlowQuery): LaorV4DashboardDailyFlowPage {
            dailyFlowQueries += query
            val resolvedAsOfDate = query.command.asOfDate ?: LocalDate.parse("2024-01-05")
            return LaorV4DashboardDailyFlowPage(
                symbol = query.command.symbol,
                market = query.command.market,
                startDate = query.command.startDate,
                requestedAsOfDate = query.command.asOfDate,
                resolvedAsOfDate = resolvedAsOfDate,
                page = query.page,
                size = query.size,
                total = 1,
                items = listOf(
                    LaorV4DashboardDailyFlowItem(
                        date = LocalDate.parse("2024-01-03"),
                        referenceDate = LocalDate.parse("2024-01-02"),
                        cycleNo = 1,
                        previousClose = "100".toBigDecimal(),
                        close = "101".toBigDecimal(),
                        orders = emptyList(),
                        filledOrders = emptyList(),
                        before = dailyState(holdingQuantity = 0),
                        after = dailyState(holdingQuantity = 1),
                        dailyDividendIncome = "0".toBigDecimal(),
                        cycleClosed = false,
                        tradingCompleted = false,
                    ),
                ),
            )
        }

        override fun findIndex(query: FindLaorV4DashboardIndexQuery): LaorV4DashboardIndexResponse {
            indexQueries += query
            val resolvedAsOfDate = query.command.asOfDate ?: LocalDate.parse("2024-01-05")
            return LaorV4DashboardIndexResponse(
                symbol = query.command.symbol,
                market = query.command.market,
                startDate = query.command.startDate,
                requestedAsOfDate = query.command.asOfDate,
                resolvedAsOfDate = resolvedAsOfDate,
                base = LaorV4DashboardIndexBase(
                    baseDate = query.command.startDate,
                    baseValue = "100".toBigDecimal(),
                    initialCash = query.command.initialCash,
                    benchmarkSymbol = query.command.symbol,
                    benchmarkClose = "100".toBigDecimal(),
                ),
                series = listOf(
                    LaorV4DashboardChartSeries(
                        key = "laorIndex",
                        label = "LAOR ${query.command.symbol}",
                        chartType = "LineSeries",
                        color = "#0f766e",
                        data = listOf(
                            LaorV4DashboardChartPoint(
                                time = query.command.startDate,
                                value = "100".toBigDecimal(),
                            ),
                        ),
                    ),
                ),
                points = listOf(
                    LaorV4DashboardIndexPoint(
                        date = query.command.startDate,
                        laorIndex = "100".toBigDecimal(),
                        benchmarkIndex = "100".toBigDecimal(),
                        netEquity = query.command.initialCash,
                        cash = query.command.initialCash,
                        holdingQuantity = 0,
                        averagePurchasePrice = "0".toBigDecimal(),
                        realizedProfitLoss = "0".toBigDecimal(),
                        dividendIncome = "0".toBigDecimal(),
                        progressRound = "0".toBigDecimal(),
                        cycleNo = 1,
                        mode = "NORMAL",
                        close = "100".toBigDecimal(),
                        benchmarkClose = "100".toBigDecimal(),
                        basePoint = true,
                    ),
                ),
            )
        }

        private fun dailyState(holdingQuantity: Long): LaorV4DashboardDailyState {
            return LaorV4DashboardDailyState(
                cycleNo = 1,
                mode = "NORMAL",
                progressRound = "1.0".toBigDecimal(),
                cash = "900".toBigDecimal(),
                holdingQuantity = holdingQuantity,
                averagePurchasePrice = "100".toBigDecimal(),
                realizedProfitLoss = "0".toBigDecimal(),
                dividendIncome = "0".toBigDecimal(),
            )
        }
    }
}

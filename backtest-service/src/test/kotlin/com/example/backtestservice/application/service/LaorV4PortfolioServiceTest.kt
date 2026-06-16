package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.CalculateLaorV4DashboardCommand
import com.example.backtestservice.application.port.`in`.CalculateLaorV4DashboardUseCase
import com.example.backtestservice.application.port.`in`.CalculateLaorV4PortfolioDashboardQuery
import com.example.backtestservice.application.port.`in`.CreateLaorV4PortfolioCommand
import com.example.backtestservice.application.port.`in`.LaorV4DashboardCurrentState
import com.example.backtestservice.application.port.`in`.LaorV4DashboardCycleSummary
import com.example.backtestservice.application.port.`in`.LaorV4DashboardDataCoverage
import com.example.backtestservice.application.port.`in`.LaorV4DashboardNextOrderContext
import com.example.backtestservice.application.port.`in`.LaorV4DashboardParameters
import com.example.backtestservice.application.port.`in`.LaorV4DashboardResponse
import com.example.backtestservice.application.port.`in`.LaorV4DashboardValuation
import com.example.backtestservice.application.port.out.LaorV4PortfolioStorePort
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
        val service = LaorV4PortfolioService(store, FakeCalculateLaorV4DashboardUseCase())

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
        val dashboard = FakeCalculateLaorV4DashboardUseCase()
        val service = LaorV4PortfolioService(store, dashboard)
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
            FakeCalculateLaorV4DashboardUseCase(),
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

    private class FakeCalculateLaorV4DashboardUseCase : CalculateLaorV4DashboardUseCase {
        val commands = mutableListOf<CalculateLaorV4DashboardCommand>()

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
    }
}

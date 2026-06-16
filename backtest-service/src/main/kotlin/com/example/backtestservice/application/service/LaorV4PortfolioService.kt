package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.CalculateLaorV4DashboardCommand
import com.example.backtestservice.application.port.`in`.CalculateLaorV4DashboardUseCase
import com.example.backtestservice.application.port.`in`.CalculateLaorV4PortfolioDashboardQuery
import com.example.backtestservice.application.port.`in`.CalculateLaorV4PortfolioDashboardUseCase
import com.example.backtestservice.application.port.`in`.CreateLaorV4PortfolioCommand
import com.example.backtestservice.application.port.`in`.CreateLaorV4PortfolioUseCase
import com.example.backtestservice.application.port.`in`.FindLaorV4PortfolioUseCase
import com.example.backtestservice.application.port.`in`.LaorV4DashboardResponse
import com.example.backtestservice.application.port.`in`.LaorV4PortfolioDashboardResponse
import com.example.backtestservice.application.port.`in`.LaorV4PortfolioResponse
import com.example.backtestservice.application.port.`in`.LaorV4PortfolioSnapshotResponse
import com.example.backtestservice.application.port.out.LaorV4PortfolioStorePort
import com.example.backtestservice.domain.backtest.LaorV4PortfolioRecord
import com.example.backtestservice.domain.backtest.LaorV4PortfolioSnapshot
import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyCostPolicy
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@UseCaseImpl
class LaorV4PortfolioService(
    private val laorV4PortfolioStorePort: LaorV4PortfolioStorePort,
    private val calculateLaorV4DashboardUseCase: CalculateLaorV4DashboardUseCase,
) : CreateLaorV4PortfolioUseCase,
    FindLaorV4PortfolioUseCase,
    CalculateLaorV4PortfolioDashboardUseCase {
    override fun create(command: CreateLaorV4PortfolioCommand): LaorV4PortfolioResponse {
        command.validate()
        val now = Instant.now()
        val record = LaorV4PortfolioRecord(
            portfolioId = UUID.randomUUID(),
            name = command.name?.trim()?.takeIf { it.isNotBlank() },
            symbol = command.symbol.trim().uppercase(),
            market = command.market.trim().uppercase(),
            startDate = command.startDate,
            initialCash = command.initialCash,
            totalSplitCount = command.totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = command.firstBuyLimitPercentAbovePreviousClose,
            autoRestart = command.autoRestart,
            dividendReinvestment = command.dividendReinvestment,
            autoAdjust = command.autoAdjust,
            commissionRate = command.commissionRate,
            slippageRate = command.slippageRate,
            createdAt = now,
            updatedAt = now,
        )
        laorV4PortfolioStorePort.save(record)
        return record.toResponse()
    }

    override fun find(portfolioId: UUID): LaorV4PortfolioResponse {
        return findRecord(portfolioId).toResponse()
    }

    override fun findAll(): List<LaorV4PortfolioResponse> {
        return laorV4PortfolioStorePort.findAll()
            .sortedByDescending { it.updatedAt }
            .map { it.toResponse() }
    }

    override fun calculate(query: CalculateLaorV4PortfolioDashboardQuery): LaorV4PortfolioDashboardResponse {
        query.validate()
        val record = findRecord(query.portfolioId)
        val dashboard = calculateLaorV4DashboardUseCase.calculate(record.toDashboardCommand(query))
        val updatedRecord = record.copy(
            latestSnapshot = dashboard.toSnapshot(),
            updatedAt = Instant.now(),
        )
        laorV4PortfolioStorePort.save(updatedRecord)
        return LaorV4PortfolioDashboardResponse(
            portfolio = updatedRecord.toResponse(),
            dashboard = dashboard,
        )
    }

    private fun findRecord(portfolioId: UUID): LaorV4PortfolioRecord {
        return laorV4PortfolioStorePort.findById(portfolioId)
            ?: throw NoSuchElementException("LAOR_V4 portfolio not found: $portfolioId")
    }

    private fun CreateLaorV4PortfolioCommand.validate() {
        require(symbol.isNotBlank()) { "symbol is required" }
        require(market.isNotBlank()) { "market is required" }
        require(initialCash > BigDecimal.ZERO) { "initialCash must be positive" }
        require(commissionRate >= BigDecimal.ZERO) { "commissionRate must be zero or positive" }
        require(slippageRate >= BigDecimal.ZERO) { "slippageRate must be zero or positive" }
        LaorV4StrategyConfig(
            symbol = LaorV4StrategySymbol.valueOf(symbol.trim().uppercase()),
            totalSplitCount = totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = firstBuyLimitPercentAbovePreviousClose,
            costPolicy = LaorV4StrategyCostPolicy(
                commissionRate = commissionRate.toDouble(),
                slippageRate = slippageRate.toDouble(),
            ),
        )
    }

    private fun CalculateLaorV4PortfolioDashboardQuery.validate() {
        require(marketDataTimeoutSeconds > 0) { "marketDataTimeoutSeconds must be positive" }
    }

    private fun LaorV4PortfolioRecord.toDashboardCommand(query: CalculateLaorV4PortfolioDashboardQuery): CalculateLaorV4DashboardCommand {
        return CalculateLaorV4DashboardCommand(
            symbol = symbol,
            market = market,
            startDate = startDate,
            asOfDate = query.asOfDate,
            initialCash = initialCash,
            totalSplitCount = totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = firstBuyLimitPercentAbovePreviousClose,
            autoRestart = autoRestart,
            dividendReinvestment = dividendReinvestment,
            autoAdjust = autoAdjust,
            commissionRate = commissionRate,
            slippageRate = slippageRate,
            marketDataTimeoutSeconds = query.marketDataTimeoutSeconds,
        )
    }

    private fun LaorV4DashboardResponse.toSnapshot(): LaorV4PortfolioSnapshot {
        return LaorV4PortfolioSnapshot(
            resolvedAsOfDate = resolvedAsOfDate,
            calculatedAt = Instant.now(),
            cycleNo = current.cycleNo,
            mode = current.mode,
            progressRound = current.progressRound,
            cash = current.cash,
            holdingQuantity = current.holdingQuantity,
            averagePurchasePrice = current.averagePurchasePrice,
            realizedProfitLoss = current.realizedProfitLoss,
            dividendIncome = current.dividendIncome,
            netEquity = valuation.netEquity,
            totalProfitLoss = valuation.totalProfitLoss,
            totalReturnPercent = valuation.totalReturnPercent,
        )
    }

    private fun LaorV4PortfolioRecord.toResponse(): LaorV4PortfolioResponse {
        return LaorV4PortfolioResponse(
            portfolioId = portfolioId,
            name = name,
            symbol = symbol,
            market = market,
            startDate = startDate,
            initialCash = initialCash,
            totalSplitCount = totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = firstBuyLimitPercentAbovePreviousClose,
            autoRestart = autoRestart,
            dividendReinvestment = dividendReinvestment,
            autoAdjust = autoAdjust,
            commissionRate = commissionRate,
            slippageRate = slippageRate,
            latestSnapshot = latestSnapshot?.toResponse(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun LaorV4PortfolioSnapshot.toResponse(): LaorV4PortfolioSnapshotResponse {
        return LaorV4PortfolioSnapshotResponse(
            resolvedAsOfDate = resolvedAsOfDate,
            calculatedAt = calculatedAt,
            cycleNo = cycleNo,
            mode = mode,
            progressRound = progressRound,
            cash = cash,
            holdingQuantity = holdingQuantity,
            averagePurchasePrice = averagePurchasePrice,
            realizedProfitLoss = realizedProfitLoss,
            dividendIncome = dividendIncome,
            netEquity = netEquity,
            totalProfitLoss = totalProfitLoss,
            totalReturnPercent = totalReturnPercent,
        )
    }
}

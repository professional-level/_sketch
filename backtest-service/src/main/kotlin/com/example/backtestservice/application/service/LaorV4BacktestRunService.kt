package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.ExecuteBacktestRunCommand
import com.example.backtestservice.application.port.`in`.ExecuteBacktestRunUseCase
import com.example.backtestservice.application.port.`in`.FindLaorV4BacktestRunUseCase
import com.example.backtestservice.application.port.`in`.LaorV4BacktestCycleResult
import com.example.backtestservice.application.port.`in`.LaorV4BacktestParameters
import com.example.backtestservice.application.port.`in`.LaorV4BacktestRunResponse
import com.example.backtestservice.application.port.`in`.RunLaorV4BacktestCommand
import com.example.backtestservice.application.port.`in`.RunLaorV4BacktestUseCase
import com.example.backtestservice.application.port.out.BacktestRunStorePort
import com.example.backtestservice.domain.backtest.BacktestEquityPoint
import com.example.backtestservice.domain.backtest.BacktestRunRecord
import com.example.backtestservice.domain.backtest.BacktestStrategyType
import com.example.backtestservice.domain.backtest.BacktestTradeSide
import com.example.common.UseCaseImpl
import java.math.BigDecimal
import java.math.MathContext
import java.util.UUID

@UseCaseImpl
class LaorV4BacktestRunService(
    private val executeBacktestRunUseCase: ExecuteBacktestRunUseCase,
    private val backtestRunStorePort: BacktestRunStorePort,
) : RunLaorV4BacktestUseCase, FindLaorV4BacktestRunUseCase {
    override fun execute(command: RunLaorV4BacktestCommand): LaorV4BacktestRunResponse {
        val summary = executeBacktestRunUseCase.execute(command.toExecuteCommand())
        return find(summary.runId)
    }

    override fun find(runId: UUID): LaorV4BacktestRunResponse {
        val record = backtestRunStorePort.findById(runId)
            ?: throw NoSuchElementException("backtest run not found: $runId")
        require(record.summary.strategyType == BacktestStrategyType.LAOR_V4) {
            "backtest run is not LAOR_V4: $runId"
        }
        return record.toLaorResponse()
    }

    private fun RunLaorV4BacktestCommand.toExecuteCommand(): ExecuteBacktestRunCommand {
        return ExecuteBacktestRunCommand(
            symbol = symbol,
            market = market,
            from = from,
            to = to,
            initialCash = initialCash,
            strategyType = BacktestStrategyType.LAOR_V4,
            laorV4 = LaorV4BacktestParameters(
                totalSplitCount = totalSplitCount,
                firstBuyLimitMultiplier = firstBuyLimitMultiplier,
                autoRestart = autoRestart,
            ),
            refreshMarketData = refreshMarketData,
            autoAdjust = autoAdjust,
            marketDataTimeoutSeconds = marketDataTimeoutSeconds,
        )
    }

    private fun BacktestRunRecord.toLaorResponse(): LaorV4BacktestRunResponse {
        val finalPoint = equityCurve.lastOrNull()
            ?: throw IllegalStateException("LAOR_V4 run has no equity curve: ${summary.runId}")
        val finalHoldingMarketValue = finalPoint.holdingMarketValue()
        val finalPositionCost = finalPoint.averagePurchasePrice.orZero() * finalPoint.positionQuantity.toBigDecimal()
        val finalHoldingUnrealizedProfitLoss = finalHoldingMarketValue - finalPositionCost
        val realizedProfitLoss = finalPoint.realizedProfitLoss.orZero()
        val finalProfit = summary.finalEquity - summary.initialCash
        val cycles = toCycleResults()
        return LaorV4BacktestRunResponse(
            runId = summary.runId,
            status = summary.status,
            symbol = summary.symbol,
            market = summary.market,
            from = summary.from,
            to = summary.to,
            initialCash = summary.initialCash,
            finalEquity = summary.finalEquity,
            finalProfit = finalProfit,
            finalReturnPercent = finalProfit.percentOf(summary.initialCash),
            realizedProfitLoss = realizedProfitLoss,
            realizedProfitLossPercent = realizedProfitLoss.percentOf(summary.initialCash),
            finalCash = finalPoint.cash,
            finalHoldingQuantity = finalPoint.positionQuantity,
            finalHoldingAveragePrice = finalPoint.averagePurchasePrice.orZero(),
            finalHoldingClose = finalPoint.close,
            finalHoldingMarketValue = finalHoldingMarketValue,
            finalHoldingMarketValuePercent = finalHoldingMarketValue.percentOf(summary.finalEquity),
            finalHoldingUnrealizedProfitLoss = finalHoldingUnrealizedProfitLoss,
            finalHoldingUnrealizedReturnPercent = finalHoldingUnrealizedProfitLoss.percentOf(finalPositionCost),
            tradeCount = summary.tradeCount,
            equityPointCount = summary.equityPointCount,
            cycleCount = cycles.size,
            cycles = cycles,
        )
    }

    private fun BacktestRunRecord.toCycleResults(): List<LaorV4BacktestCycleResult> {
        var previousCycleRealizedProfitLoss = BigDecimal.ZERO
        return equityCurve
            .groupBy { it.cycleNo ?: 1 }
            .toSortedMap()
            .map { (cycleNo, points) ->
                val orderedPoints = points.sortedBy { it.date }
                val firstPoint = orderedPoints.first()
                val lastPoint = orderedPoints.last()
                val cycleTrades = trades.filter { it.cycleNo == cycleNo }
                val cumulativeRealizedProfitLoss = lastPoint.realizedProfitLoss.orZero()
                val cycleRealizedProfitLoss = cumulativeRealizedProfitLoss - previousCycleRealizedProfitLoss
                previousCycleRealizedProfitLoss = cumulativeRealizedProfitLoss
                val endingHoldingMarketValue = lastPoint.holdingMarketValue()
                LaorV4BacktestCycleResult(
                    cycleNo = cycleNo,
                    from = firstPoint.date,
                    to = lastPoint.date,
                    tradeCount = cycleTrades.size,
                    buyCount = cycleTrades.count { it.side == BacktestTradeSide.BUY },
                    sellCount = cycleTrades.count { it.side == BacktestTradeSide.SELL },
                    realizedProfitLoss = cycleRealizedProfitLoss,
                    realizedProfitLossPercent = cycleRealizedProfitLoss.percentOf(summary.initialCash),
                    endingEquity = lastPoint.equity,
                    endingCash = lastPoint.cash,
                    endingHoldingQuantity = lastPoint.positionQuantity,
                    endingHoldingMarketValue = endingHoldingMarketValue,
                    endingHoldingMarketValuePercent = endingHoldingMarketValue.percentOf(lastPoint.equity),
                    closed = lastPoint.positionQuantity == 0L,
                )
            }
    }

    private fun BacktestEquityPoint.holdingMarketValue(): BigDecimal {
        return close * positionQuantity.toBigDecimal()
    }

    private fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO

    private fun BigDecimal.percentOf(base: BigDecimal): BigDecimal {
        return if (base.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO
        } else {
            divide(base, MathContext.DECIMAL64) * PERCENT_MULTIPLIER
        }
    }

    private operator fun BigDecimal.plus(other: BigDecimal): BigDecimal = add(other)
    private operator fun BigDecimal.minus(other: BigDecimal): BigDecimal = subtract(other)
    private operator fun BigDecimal.times(other: BigDecimal): BigDecimal = multiply(other)

    companion object {
        private val PERCENT_MULTIPLIER = BigDecimal("100")
    }
}

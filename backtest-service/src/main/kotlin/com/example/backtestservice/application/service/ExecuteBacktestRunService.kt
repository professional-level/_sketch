package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.ExecuteBacktestRunCommand
import com.example.backtestservice.application.port.`in`.ExecuteBacktestRunUseCase
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataUseCase
import com.example.backtestservice.application.port.`in`.RunBacktestCommand
import com.example.backtestservice.application.port.`in`.RunBacktestUseCase
import com.example.backtestservice.application.port.out.BacktestRunStorePort
import com.example.backtestservice.domain.backtest.BacktestRunSummary
import com.example.backtestservice.domain.backtest.BacktestStrategyType
import com.example.common.UseCaseImpl
import java.math.BigDecimal

@UseCaseImpl
class ExecuteBacktestRunService(
    private val importHistoricalMarketDataUseCase: ImportHistoricalMarketDataUseCase,
    private val runBacktestUseCase: RunBacktestUseCase,
    private val backtestRunStorePort: BacktestRunStorePort,
) : ExecuteBacktestRunUseCase {
    override fun execute(command: ExecuteBacktestRunCommand): BacktestRunSummary {
        command.validate()
        importHistoricalMarketDataUseCase.execute(command.toImportCommand())
        val record = runBacktestUseCase.execute(command.toRunBacktestCommand()).toRunRecord()
        backtestRunStorePort.save(record)
        return record.summary
    }

    private fun ExecuteBacktestRunCommand.validate() {
        require(symbol.isNotBlank()) { "symbol is required" }
        require(!from.isAfter(to)) { "from must be on or before to" }
        require(initialCash > BigDecimal.ZERO) { "initialCash must be positive" }
        require(commissionRate >= BigDecimal.ZERO) { "commissionRate must be zero or positive" }
        require(slippageRate >= BigDecimal.ZERO) { "slippageRate must be zero or positive" }
        require(marketDataTimeoutSeconds > 0) { "marketDataTimeoutSeconds must be positive" }
    }

    private fun ExecuteBacktestRunCommand.toImportCommand(): ImportHistoricalMarketDataCommand {
        return ImportHistoricalMarketDataCommand(
            symbol = symbol,
            market = market,
            from = marketDataImportFrom(),
            to = to,
            autoAdjust = autoAdjust,
            timeoutSeconds = marketDataTimeoutSeconds,
        )
    }

    private fun ExecuteBacktestRunCommand.toRunBacktestCommand(): RunBacktestCommand {
        return RunBacktestCommand(
            symbol = symbol,
            market = market,
            from = from,
            to = to,
            initialCash = initialCash,
            strategyType = strategyType,
            commissionRate = commissionRate,
            slippageRate = slippageRate,
            laorV4 = laorV4,
        )
    }

    private fun ExecuteBacktestRunCommand.marketDataImportFrom() =
        if (strategyType == BacktestStrategyType.LAOR_V4) from.minusDays(LAOR_V4_MARKET_DATA_LOOKBACK_DAYS) else from

    companion object {
        private const val LAOR_V4_MARKET_DATA_LOOKBACK_DAYS = 14L
    }
}

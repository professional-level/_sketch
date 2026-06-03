package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataResult
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataUseCase
import com.example.backtestservice.application.port.out.ExternalHistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.ExternalHistoricalMarketDataPort
import com.example.backtestservice.application.port.out.HistoricalMarketDataPort
import com.example.backtestservice.application.port.out.SaveHistoricalDailyCandlesCommand
import com.example.common.UseCaseImpl

@UseCaseImpl
class ImportHistoricalMarketDataService(
    private val externalHistoricalMarketDataPort: ExternalHistoricalMarketDataPort,
    private val historicalMarketDataPort: HistoricalMarketDataPort,
) : ImportHistoricalMarketDataUseCase {
    override fun execute(command: ImportHistoricalMarketDataCommand): ImportHistoricalMarketDataResult {
        command.validate()
        val candles = externalHistoricalMarketDataPort.fetchDailyCandles(
            ExternalHistoricalDailyCandlesQuery(
                symbol = command.symbol,
                market = command.market,
                from = command.from,
                to = command.to,
                autoAdjust = command.autoAdjust,
                timeoutSeconds = command.timeoutSeconds,
            ),
        )
        require(candles.isNotEmpty()) {
            "external historical candles not found: symbol=${command.symbol}, from=${command.from}, to=${command.to}"
        }
        historicalMarketDataPort.saveDailyCandles(
            SaveHistoricalDailyCandlesCommand(
                symbol = command.symbol,
                market = command.market,
                candles = candles,
            ),
        )
        return ImportHistoricalMarketDataResult(
            symbol = command.symbol.trim().uppercase(),
            market = command.market.trim().uppercase(),
            importedCount = candles.size,
            from = candles.first().date,
            to = candles.last().date,
        )
    }

    private fun ImportHistoricalMarketDataCommand.validate() {
        require(symbol.isNotBlank()) { "symbol is required" }
        require(!from.isAfter(to)) { "from must be on or before to" }
        require(timeoutSeconds > 0) { "timeoutSeconds must be positive" }
    }
}

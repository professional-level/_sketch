package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataResult
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataUseCase
import com.example.backtestservice.application.port.out.ExternalHistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.ExternalHistoricalMarketDataPort
import com.example.backtestservice.application.port.out.HistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.HistoricalMarketDataPort
import com.example.backtestservice.application.port.out.SaveHistoricalDailyCandlesCommand
import com.example.backtestservice.domain.market.HistoricalCandle
import com.example.common.UseCaseImpl
import java.time.LocalDate

@UseCaseImpl
class ImportHistoricalMarketDataService(
    private val externalHistoricalMarketDataPort: ExternalHistoricalMarketDataPort,
    private val historicalMarketDataPort: HistoricalMarketDataPort,
) : ImportHistoricalMarketDataUseCase {
    override fun execute(command: ImportHistoricalMarketDataCommand): ImportHistoricalMarketDataResult {
        command.validate()
        val cachedCandles = historicalMarketDataPort.findDailyCandles(command.toCachedQuery())
            .sortedBy { it.date }
        val fetchedCandles = command.missingRanges(cachedCandles)
            .flatMap { range ->
                externalHistoricalMarketDataPort.fetchDailyCandles(command.toExternalQuery(range))
            }
            .distinctBy { it.date }
            .sortedBy { it.date }
        if (fetchedCandles.isNotEmpty()) {
            historicalMarketDataPort.saveDailyCandles(
                SaveHistoricalDailyCandlesCommand(
                    symbol = command.symbol,
                    market = command.market,
                    candles = fetchedCandles,
                ),
            )
        }

        val availableCandles = (cachedCandles + fetchedCandles)
            .distinctBy { it.date }
            .filter { it.date >= command.from && it.date <= command.to }
            .sortedBy { it.date }
        require(availableCandles.isNotEmpty()) {
            "external historical candles not found: symbol=${command.symbol}, from=${command.from}, to=${command.to}"
        }
        return ImportHistoricalMarketDataResult(
            symbol = command.symbol.trim().uppercase(),
            market = command.market.trim().uppercase(),
            importedCount = fetchedCandles.size,
            from = availableCandles.first().date,
            to = availableCandles.last().date,
        )
    }

    private fun ImportHistoricalMarketDataCommand.validate() {
        require(symbol.isNotBlank()) { "symbol is required" }
        require(!from.isAfter(to)) { "from must be on or before to" }
        require(timeoutSeconds > 0) { "timeoutSeconds must be positive" }
    }

    private fun ImportHistoricalMarketDataCommand.toCachedQuery(): HistoricalDailyCandlesQuery {
        return HistoricalDailyCandlesQuery(
            symbol = symbol,
            market = market,
            from = from,
            to = to,
        )
    }

    private fun ImportHistoricalMarketDataCommand.toExternalQuery(range: DateRange): ExternalHistoricalDailyCandlesQuery {
        return ExternalHistoricalDailyCandlesQuery(
            symbol = symbol,
            market = market,
            from = range.from,
            to = range.to,
            autoAdjust = autoAdjust,
            timeoutSeconds = timeoutSeconds,
        )
    }

    private fun ImportHistoricalMarketDataCommand.missingRanges(
        cachedCandles: List<HistoricalCandle>,
    ): List<DateRange> {
        if (cachedCandles.isEmpty()) return listOf(DateRange(from, to))

        val ranges = mutableListOf<DateRange>()
        val firstCachedDate = cachedCandles.first().date
        val lastCachedDate = cachedCandles.last().date
        if (firstCachedDate > from.plusDays(CACHE_EDGE_TOLERANCE_DAYS)) {
            ranges += DateRange(from, firstCachedDate.minusDays(1))
        }
        cachedCandles.zipWithNext().forEach { (left, right) ->
            if (right.date > left.date.plusDays(CACHE_GAP_TOLERANCE_DAYS)) {
                ranges += DateRange(left.date.plusDays(1), right.date.minusDays(1))
            }
        }
        if (lastCachedDate < to.minusDays(CACHE_EDGE_TOLERANCE_DAYS)) {
            ranges += DateRange(lastCachedDate.plusDays(1), to)
        }
        return ranges.filterNot { it.from.isAfter(it.to) }
    }

    private data class DateRange(
        val from: LocalDate,
        val to: LocalDate,
    )

    companion object {
        private const val CACHE_EDGE_TOLERANCE_DAYS = 7L
        private const val CACHE_GAP_TOLERANCE_DAYS = 7L
    }
}

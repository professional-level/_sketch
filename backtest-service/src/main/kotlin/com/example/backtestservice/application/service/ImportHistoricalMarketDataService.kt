package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataResult
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataUseCase
import com.example.backtestservice.application.port.out.ExternalHistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.ExternalHistoricalMarketDataPort
import com.example.backtestservice.application.port.out.HistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.HistoricalMarketDataPort
import com.example.backtestservice.application.port.out.MarketCalendarPort
import com.example.backtestservice.application.port.out.MarketTradingDaysQuery
import com.example.backtestservice.application.port.out.SaveHistoricalDailyCandlesCommand
import com.example.backtestservice.domain.market.HistoricalCandle
import com.example.common.UseCaseImpl
import java.time.LocalDate

@UseCaseImpl
class ImportHistoricalMarketDataService(
    private val externalHistoricalMarketDataPort: ExternalHistoricalMarketDataPort,
    private val historicalMarketDataPort: HistoricalMarketDataPort,
    private val marketCalendarPort: MarketCalendarPort,
) : ImportHistoricalMarketDataUseCase {
    override fun execute(command: ImportHistoricalMarketDataCommand): ImportHistoricalMarketDataResult {
        command.validate()
        val tradingDays = marketCalendarPort.findTradingDays(command.toTradingDaysQuery()).dates
            .filter { it >= command.from && it <= command.to }
            .distinct()
            .sorted()
        require(tradingDays.isNotEmpty()) {
            "trading days not found: market=${command.market}, from=${command.from}, to=${command.to}"
        }
        val cachedCandles = historicalMarketDataPort.findDailyCandles(command.toCachedQuery())
            .sortedBy { it.date }
        val fetchedCandles = command.missingRanges(cachedCandles, tradingDays)
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
        val missingTradingDays = tradingDays - availableCandles.map { it.date }.toSet()
        require(missingTradingDays.isEmpty()) {
            "historical candles missing for trading days: symbol=${command.symbol}, " +
                "from=${command.from}, to=${command.to}, missing=${missingTradingDays.toPreview()}"
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

    private fun ImportHistoricalMarketDataCommand.toTradingDaysQuery(): MarketTradingDaysQuery {
        return MarketTradingDaysQuery(
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
        tradingDays: List<LocalDate>,
    ): List<DateRange> {
        val cachedDates = cachedCandles.map { it.date }.toSet()
        val ranges = mutableListOf<DateRange>()
        var rangeStart: LocalDate? = null
        var previousMissing: LocalDate? = null
        tradingDays.forEach { date ->
            if (date in cachedDates) {
                if (rangeStart != null && previousMissing != null) {
                    ranges += DateRange(checkNotNull(rangeStart), checkNotNull(previousMissing))
                    rangeStart = null
                    previousMissing = null
                }
            } else {
                if (rangeStart == null) {
                    rangeStart = date
                }
                previousMissing = date
            }
        }
        if (rangeStart != null && previousMissing != null) {
            ranges += DateRange(checkNotNull(rangeStart), checkNotNull(previousMissing))
        }
        return ranges
    }

    private fun List<LocalDate>.toPreview(): String {
        val sortedDates = sorted()
        val suffix = if (sortedDates.size > MAX_MISSING_DATES_IN_ERROR) {
            ", ... total=${sortedDates.size}"
        } else {
            ""
        }
        return sortedDates.take(MAX_MISSING_DATES_IN_ERROR).joinToString(prefix = "[", postfix = "$suffix]")
    }

    private data class DateRange(
        val from: LocalDate,
        val to: LocalDate,
    )

    companion object {
        private const val MAX_MISSING_DATES_IN_ERROR = 10
    }
}

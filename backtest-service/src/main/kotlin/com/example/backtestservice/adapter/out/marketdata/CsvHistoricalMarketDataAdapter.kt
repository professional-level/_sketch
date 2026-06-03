package com.example.backtestservice.adapter.out.marketdata

import com.example.backtestservice.application.port.out.HistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.HistoricalMarketDataPort
import com.example.backtestservice.application.port.out.SaveHistoricalDailyCandlesCommand
import com.example.backtestservice.domain.market.HistoricalCandle
import com.example.common.PersistenceAdapter
import org.springframework.beans.factory.annotation.Value
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

@PersistenceAdapter
class CsvHistoricalMarketDataAdapter(
    @Value("\${akra.backtest.market-data.csv-root:data/yfinance}")
    private val csvRoot: String,
) : HistoricalMarketDataPort {
    override fun findDailyCandles(query: HistoricalDailyCandlesQuery): List<HistoricalCandle> {
        val path = Path.of(csvRoot).resolve(query.symbol.toCsvFileName())
        if (!Files.exists(path)) return emptyList()

        val lines = Files.readAllLines(path)
            .filter { it.isNotBlank() }
        if (lines.size <= 1) return emptyList()

        val columns = lines.first().split(",").mapIndexed { index, name -> name.normalizedHeader() to index }.toMap()
        return lines.drop(1)
            .map { it.toHistoricalCandle(columns, query) }
            .filter { it.date >= query.from && it.date <= query.to }
            .sortedBy { it.date }
    }

    override fun saveDailyCandles(command: SaveHistoricalDailyCandlesCommand) {
        if (command.candles.isEmpty()) return
        val root = Path.of(csvRoot)
        Files.createDirectories(root)
        val path = root.resolve(command.symbol.toCsvFileName())
        val rows = sequenceOf("date,open,high,low,close,adj_close,volume") +
            command.candles
                .sortedBy { it.date }
                .asSequence()
                .map {
                    listOf(
                        it.date.toString(),
                        it.open.toPlainString(),
                        it.high.toPlainString(),
                        it.low.toPlainString(),
                        it.close.toPlainString(),
                        it.adjustedClose.toPlainString(),
                        it.volume.toString(),
                    ).joinToString(",")
                }
        Files.writeString(path, rows.joinToString(System.lineSeparator()))
    }

    private fun String.toHistoricalCandle(
        columns: Map<String, Int>,
        query: HistoricalDailyCandlesQuery,
    ): HistoricalCandle {
        val values = split(",")
        return HistoricalCandle(
            symbol = query.symbol.trim().uppercase(),
            market = query.market.trim().uppercase(),
            date = LocalDate.parse(values.required(columns, "date")),
            open = values.required(columns, "open").toBigDecimal(),
            high = values.required(columns, "high").toBigDecimal(),
            low = values.required(columns, "low").toBigDecimal(),
            close = values.required(columns, "close").toBigDecimal(),
            adjustedClose = values.optional(columns, "adj_close")
                ?.toBigDecimal()
                ?: values.required(columns, "close").toBigDecimal(),
            volume = values.required(columns, "volume").toLong(),
            source = "YFINANCE",
        )
    }

    private fun List<String>.required(columns: Map<String, Int>, name: String): String {
        return optional(columns, name)
            ?: throw IllegalArgumentException("CSV is missing required column or value: $name")
    }

    private fun List<String>.optional(columns: Map<String, Int>, name: String): String? {
        val index = columns[name] ?: return null
        return getOrNull(index)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun String.normalizedHeader(): String {
        return trim().lowercase().replace(" ", "_")
    }

    private fun String.toCsvFileName(): String {
        val safe = trim().uppercase()
            .map { char -> if (char.isLetterOrDigit() || char == '.' || char == '_' || char == '-') char else '_' }
            .joinToString("")
        return "$safe.csv"
    }
}

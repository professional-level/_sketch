package com.example.backtestservice.application.port.`in`

import com.example.common.UseCase
import java.time.LocalDate

@UseCase
interface ImportHistoricalMarketDataUseCase {
    fun execute(command: ImportHistoricalMarketDataCommand): ImportHistoricalMarketDataResult
}

data class ImportHistoricalMarketDataCommand(
    val symbol: String,
    val market: String = "US",
    val from: LocalDate,
    val to: LocalDate,
    val autoAdjust: Boolean = false,
    val timeoutSeconds: Long = 30,
)

data class ImportHistoricalMarketDataResult(
    val symbol: String,
    val market: String,
    val importedCount: Int,
    val from: LocalDate,
    val to: LocalDate,
)

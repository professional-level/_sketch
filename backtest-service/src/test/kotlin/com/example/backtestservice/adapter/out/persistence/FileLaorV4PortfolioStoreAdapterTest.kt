package com.example.backtestservice.adapter.out.persistence

import com.example.backtestservice.domain.backtest.LaorV4PortfolioRecord
import com.example.backtestservice.domain.backtest.LaorV4PortfolioSnapshot
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.io.TempDir
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FileLaorV4PortfolioStoreAdapterTest {
    @TempDir
    lateinit var tempDir: java.nio.file.Path

    @Test
    fun `saves loads and lists Laor V4 portfolio records`() {
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val adapter = FileLaorV4PortfolioStoreAdapter(tempDir.toString(), objectMapper)
        val record = record()

        adapter.save(record)

        val loaded = adapter.findById(record.portfolioId)
        val all = adapter.findAll()

        assertNotNull(loaded)
        assertEquals(record.portfolioId, loaded.portfolioId)
        assertEquals("TQQQ", loaded.symbol)
        assertEquals(LocalDate.parse("2024-01-05"), loaded.latestSnapshot?.resolvedAsOfDate)
        assertEquals(listOf(record.portfolioId), all.map { it.portfolioId })
    }

    @Test
    fun `returns null and empty list when portfolio files do not exist`() {
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val adapter = FileLaorV4PortfolioStoreAdapter(tempDir.toString(), objectMapper)

        assertEquals(null, adapter.findById(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
        assertEquals(emptyList(), adapter.findAll())
    }

    private fun record(): LaorV4PortfolioRecord {
        val now = Instant.parse("2024-01-05T00:00:00Z")
        return LaorV4PortfolioRecord(
            portfolioId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            name = "Main",
            symbol = "TQQQ",
            market = "US",
            startDate = LocalDate.parse("2024-01-02"),
            initialCash = "10000".toBigDecimal(),
            totalSplitCount = 30,
            firstBuyLimitPercentAbovePreviousClose = 12.0,
            latestSnapshot = LaorV4PortfolioSnapshot(
                resolvedAsOfDate = LocalDate.parse("2024-01-05"),
                calculatedAt = now,
                cycleNo = 1,
                mode = "NORMAL",
                progressRound = "1.0".toBigDecimal(),
                cash = "9500".toBigDecimal(),
                holdingQuantity = 5,
                averagePurchasePrice = "100".toBigDecimal(),
                realizedProfitLoss = "0".toBigDecimal(),
                dividendIncome = "0".toBigDecimal(),
                netEquity = "10000".toBigDecimal(),
                totalProfitLoss = "0".toBigDecimal(),
                totalReturnPercent = "0".toBigDecimal(),
            ),
            createdAt = now,
            updatedAt = now,
        )
    }
}

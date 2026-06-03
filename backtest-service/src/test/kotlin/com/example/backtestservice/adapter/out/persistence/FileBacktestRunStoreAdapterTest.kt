package com.example.backtestservice.adapter.out.persistence

import com.example.backtestservice.domain.backtest.BacktestEquityPoint
import com.example.backtestservice.domain.backtest.BacktestResult
import com.example.backtestservice.domain.backtest.BacktestStrategyType
import com.example.backtestservice.domain.backtest.BacktestTrade
import com.example.backtestservice.domain.backtest.BacktestTradeSide
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.io.TempDir
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FileBacktestRunStoreAdapterTest {
    @TempDir
    lateinit var tempDir: java.nio.file.Path

    @Test
    fun `saves and loads backtest run records`() {
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val adapter = FileBacktestRunStoreAdapter(tempDir.toString(), objectMapper)
        val record = record()

        adapter.save(record)

        val loaded = adapter.findById(record.summary.runId)

        assertNotNull(loaded)
        assertEquals(record.summary.runId, loaded.summary.runId)
        assertEquals("TQQQ", loaded.summary.symbol)
        assertEquals(1, loaded.trades.size)
        assertEquals(1, loaded.equityCurve.size)
    }

    @Test
    fun `returns null when run file does not exist`() {
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val adapter = FileBacktestRunStoreAdapter(tempDir.toString(), objectMapper)

        val loaded = adapter.findById(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"))

        assertEquals(null, loaded)
    }

    private fun record() = BacktestResult(
        runId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
        symbol = "TQQQ",
        market = "US",
        strategyType = BacktestStrategyType.BUY_AND_HOLD,
        from = LocalDate.parse("2024-01-02"),
        to = LocalDate.parse("2024-01-02"),
        initialCash = "1000".toBigDecimal(),
        finalEquity = "1100".toBigDecimal(),
        totalReturn = "0.1".toBigDecimal(),
        maxDrawdown = "0".toBigDecimal(),
        trades = listOf(
            BacktestTrade(
                side = BacktestTradeSide.BUY,
                symbol = "TQQQ",
                date = LocalDate.parse("2024-01-02"),
                quantity = 10,
                price = "10".toBigDecimal(),
                notional = "100".toBigDecimal(),
                commission = "0".toBigDecimal(),
            ),
        ),
        equityCurve = listOf(
            BacktestEquityPoint(
                date = LocalDate.parse("2024-01-02"),
                equity = "1100".toBigDecimal(),
                cash = "0".toBigDecimal(),
                positionQuantity = 10,
                close = "11".toBigDecimal(),
            ),
        ),
    ).toRunRecord()
}

package com.example.backtestservice.application.service

import com.example.backtestservice.application.port.`in`.ExecuteBacktestRunCommand
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataResult
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataUseCase
import com.example.backtestservice.application.port.`in`.RunBacktestCommand
import com.example.backtestservice.application.port.`in`.RunBacktestUseCase
import com.example.backtestservice.application.port.out.BacktestRunStorePort
import com.example.backtestservice.domain.backtest.BacktestEquityPoint
import com.example.backtestservice.domain.backtest.BacktestResult
import com.example.backtestservice.domain.backtest.BacktestRunRecord
import com.example.backtestservice.domain.backtest.BacktestTrade
import com.example.backtestservice.domain.backtest.BacktestTradeSide
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ExecuteBacktestRunServiceTest {
    @Test
    fun `ensures cached market data runs backtest and stores summary record`() {
        val importUseCase = FakeImportHistoricalMarketDataUseCase()
        val runUseCase = FakeRunBacktestUseCase()
        val store = FakeBacktestRunStorePort()
        val service = ExecuteBacktestRunService(
            importHistoricalMarketDataUseCase = importUseCase,
            runBacktestUseCase = runUseCase,
            backtestRunStorePort = store,
        )

        val summary = service.execute(
            ExecuteBacktestRunCommand(
                symbol = "tqqq",
                from = LocalDate.parse("2024-01-02"),
                to = LocalDate.parse("2024-01-03"),
                initialCash = "1000".toBigDecimal(),
            ),
        )

        assertEquals("TQQQ", summary.symbol)
        assertEquals(1, importUseCase.commands.size)
        assertEquals(1, runUseCase.commands.size)
        assertEquals(summary.runId, store.saved?.summary?.runId)
        assertEquals(1, store.saved?.trades?.size)
        assertEquals(2, store.saved?.equityCurve?.size)
    }

    private class FakeImportHistoricalMarketDataUseCase : ImportHistoricalMarketDataUseCase {
        val commands = mutableListOf<ImportHistoricalMarketDataCommand>()

        override fun execute(command: ImportHistoricalMarketDataCommand): ImportHistoricalMarketDataResult {
            commands += command
            return ImportHistoricalMarketDataResult(
                symbol = command.symbol.uppercase(),
                market = command.market.uppercase(),
                importedCount = 2,
                from = command.from,
                to = command.to,
            )
        }
    }

    private class FakeRunBacktestUseCase : RunBacktestUseCase {
        val commands = mutableListOf<RunBacktestCommand>()

        override fun execute(command: RunBacktestCommand): BacktestResult {
            commands += command
            return BacktestResult(
                runId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                symbol = command.symbol.uppercase(),
                market = command.market.uppercase(),
                strategyType = command.strategyType,
                from = command.from,
                to = command.to,
                initialCash = command.initialCash,
                finalEquity = "1100".toBigDecimal(),
                totalReturn = "0.1".toBigDecimal(),
                maxDrawdown = "0.05".toBigDecimal(),
                trades = listOf(
                    BacktestTrade(
                        side = BacktestTradeSide.BUY,
                        symbol = command.symbol.uppercase(),
                        date = command.from,
                        quantity = 10,
                        price = "10".toBigDecimal(),
                        notional = "100".toBigDecimal(),
                        commission = "0".toBigDecimal(),
                    ),
                ),
                equityCurve = listOf(
                    BacktestEquityPoint(
                        date = command.from,
                        equity = "1000".toBigDecimal(),
                        cash = "0".toBigDecimal(),
                        positionQuantity = 10,
                        close = "10".toBigDecimal(),
                    ),
                    BacktestEquityPoint(
                        date = command.to,
                        equity = "1100".toBigDecimal(),
                        cash = "0".toBigDecimal(),
                        positionQuantity = 10,
                        close = "11".toBigDecimal(),
                    ),
                ),
            )
        }
    }

    private class FakeBacktestRunStorePort : BacktestRunStorePort {
        var saved: BacktestRunRecord? = null

        override fun save(record: BacktestRunRecord) {
            saved = record
        }

        override fun findById(runId: UUID): BacktestRunRecord? {
            return saved?.takeIf { it.summary.runId == runId }
        }
    }
}

package com.example.stockpurchaseservice.adapter.`in`.web

import com.example.stockpurchaseservice.application.port.`in`.BrokerAccountSnapshotResult
import com.example.stockpurchaseservice.application.port.`in`.GetBrokerAccountSnapshotCommand
import com.example.stockpurchaseservice.application.port.`in`.GetBrokerAccountSnapshotUseCase
import com.example.stockpurchaseservice.application.port.out.AccountPositionSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class BrokerAccountSnapshotControllerTest {

    @Test
    fun `returns account snapshot response`() {
        val result = BrokerAccountSnapshotResult(
            generatedAt = ZonedDateTime.parse("2026-06-02T10:00:00+09:00[Asia/Seoul]"),
            snapshot = AccountSnapshotDto(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                positions = listOf(
                    AccountPositionSnapshotDto(
                        symbol = "TQQQ",
                        stockName = "ProShares UltraPro QQQ",
                        quantity = 3,
                    ),
                ),
                availableCashAmount = 1250.25,
                totalPurchaseAmount = 337.5,
                totalEvaluationAmount = 360.0,
                totalProfitLossAmount = 22.5,
            ),
        )
        val useCase = FakeGetBrokerAccountSnapshotUseCase(result)
        val controller = BrokerAccountSnapshotController(useCase)

        val response = controller.accountSnapshot(
            market = StockOrderMarket.OVERSEAS_US,
            exchange = "NASD",
            currency = "USD",
        )

        assertEquals(
            GetBrokerAccountSnapshotCommand(StockOrderMarket.OVERSEAS_US, "NASD", "USD"),
            useCase.commands.single(),
        )
        assertEquals(result.generatedAt, response.generatedAt)
        assertEquals(StockOrderMarket.OVERSEAS_US, response.market)
        assertEquals("NASD", response.exchange)
        assertEquals("USD", response.currency)
        assertEquals(result.snapshot.positions, response.positions)
        assertEquals(1250.25, response.availableCashAmount)
        assertEquals(337.5, response.totalPurchaseAmount)
        assertEquals(360.0, response.totalEvaluationAmount)
        assertEquals(22.5, response.totalProfitLossAmount)
    }

    private class FakeGetBrokerAccountSnapshotUseCase(
        private val result: BrokerAccountSnapshotResult,
    ) : GetBrokerAccountSnapshotUseCase {
        val commands: MutableList<GetBrokerAccountSnapshotCommand> = mutableListOf()

        override fun execute(command: GetBrokerAccountSnapshotCommand): BrokerAccountSnapshotResult {
            commands += command
            return result
        }
    }
}

package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.GetBrokerAccountSnapshotCommand
import com.example.stockpurchaseservice.application.port.out.AccountPositionSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.CancelOrderDto
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class GetBrokerAccountSnapshotServiceTest {

    @Test
    fun `returns generated time and broker account snapshot`() {
        val marketPort = FakeMarketServicePort(snapshot())
        val service = GetBrokerAccountSnapshotService(marketPort).apply {
            clock = Clock.fixed(
                Instant.parse("2026-06-02T01:00:00Z"),
                ZoneId.of("Asia/Seoul"),
            )
        }

        val result = service.execute(
            GetBrokerAccountSnapshotCommand(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
            ),
        )

        assertEquals(ZonedDateTime.parse("2026-06-02T10:00:00+09:00[Asia/Seoul]"), result.generatedAt)
        assertEquals(snapshot(), result.snapshot)
        assertEquals(AccountSnapshotQuery(StockOrderMarket.OVERSEAS_US, "NASD", "USD"), marketPort.queries.single())
    }

    private fun snapshot(): AccountSnapshotDto {
        return AccountSnapshotDto(
            market = StockOrderMarket.OVERSEAS_US,
            exchange = "NASD",
            currency = "USD",
            positions = listOf(
                AccountPositionSnapshotDto(
                    symbol = "TQQQ",
                    stockName = "ProShares UltraPro QQQ",
                    quantity = 3,
                    averagePurchasePrice = 112.5,
                ),
            ),
            totalPurchaseAmount = 337.5,
            totalEvaluationAmount = 360.0,
            totalProfitLossAmount = 22.5,
        )
    }

    private class FakeMarketServicePort(
        private val snapshot: AccountSnapshotDto,
    ) : MarketServicePort {
        val queries: MutableList<AccountSnapshotQuery> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            error("not used")
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            error("not used")
        }

        override fun cancelOrder(order: CancelOrderDto): BrokerOrderSubmissionDto {
            error("not used")
        }

        override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
            return emptyList()
        }

        override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
            error("not used")
        }

        override fun findAccountSnapshot(query: AccountSnapshotQuery): AccountSnapshotDto {
            queries += query
            return snapshot
        }
    }
}

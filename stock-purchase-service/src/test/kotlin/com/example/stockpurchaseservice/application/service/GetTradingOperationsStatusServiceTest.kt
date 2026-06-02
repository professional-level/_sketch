package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.AccountPositionSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.CancelOrderDto
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionProblemStatus
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionStatusCount
import com.example.stockpurchaseservice.application.port.out.OutboxStatusCount
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusPort
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusSnapshot
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionStatus
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GetTradingOperationsStatusServiceTest {

    @Test
    fun `returns generated time and trading operations snapshot`() = runBlocking {
        val snapshot = snapshot()
        val marketPort = FakeMarketServicePort(
            snapshots = mapOf(
                StockOrderMarket.DOMESTIC to AccountSnapshotDto(
                    market = StockOrderMarket.DOMESTIC,
                    exchange = "KRX",
                    currency = "KRW",
                    positions = listOf(
                        AccountPositionSnapshotDto(
                            symbol = "005930",
                            stockName = "Samsung Electronics",
                            quantity = 3,
                            averagePurchasePrice = 70_000.0,
                            currentPrice = 71_000.0,
                        ),
                    ),
                    availableCashAmount = 1_000_000.0,
                    cashCurrency = "KRW",
                    orderableCashAmount = 900_000.0,
                    totalEvaluationAmount = 213_000.0,
                ),
            ),
            failures = mapOf(StockOrderMarket.OVERSEAS_US to IllegalStateException("broker snapshot unavailable")),
        )
        val service = GetTradingOperationsStatusService(
            tradingOperationsStatusPort = FakeTradingOperationsStatusPort(snapshot),
            marketServicePort = marketPort,
            orderRiskProperties = OrderRiskProperties(),
        ).apply {
            clock = Clock.fixed(
                Instant.parse("2026-06-02T01:00:00Z"),
                ZoneId.of("Asia/Seoul"),
            )
        }

        val result = service.execute()

        assertEquals(ZonedDateTime.parse("2026-06-02T10:00:00+09:00[Asia/Seoul]"), result.generatedAt)
        assertEquals(snapshot, result.snapshot)
        assertEquals(listOf(StockOrderMarket.DOMESTIC, StockOrderMarket.OVERSEAS_US), marketPort.queries.map { it.market })
        with(result.accountSnapshots.first()) {
            assertEquals(StockOrderMarket.DOMESTIC, market)
            assertEquals(true, available)
            assertEquals(900_000.0, orderableCashAmount)
            assertEquals(3, positions.single().quantity)
            assertEquals(70_000.0, positions.single().averagePurchasePrice)
        }
        with(result.accountSnapshots.last()) {
            assertEquals(StockOrderMarket.OVERSEAS_US, market)
            assertEquals(false, available)
            assertEquals("broker snapshot unavailable", reason)
        }
    }

    private fun snapshot(): TradingOperationsStatusSnapshot {
        return TradingOperationsStatusSnapshot(
            orderSubmissionStatusCounts = listOf(
                OrderSubmissionStatusCount(OrderIntentSubmissionStatusDto.SUBMITTED, 2),
            ),
            recentProblemSubmissions = listOf(
                OrderSubmissionProblemStatus(
                    orderIntentId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    strategyExecutionId = "laor-v4:TQQQ",
                    symbol = "TQQQ",
                    market = StockOrderMarket.OVERSEAS_US,
                    side = OrderIntentSide.BUY,
                    orderType = OrderIntentType.LOC,
                    status = OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN,
                    statusReason = "broker status lookup failed",
                    externalOrderId = "broker-1",
                    submittedAt = ZonedDateTime.parse("2026-06-02T09:30:00+09:00[Asia/Seoul]"),
                    lastStatusCheckedAt = ZonedDateTime.parse("2026-06-02T09:31:00+09:00[Asia/Seoul]"),
                ),
            ),
            orderExecutionOutboxStatusCounts = listOf(
                OutboxStatusCount("PENDING", 1),
            ),
            reconciliationCursors = emptyList(),
            unmatchedExecutionCount = 1,
            recentUnmatchedExecutions = listOf(
                UnmatchedExecutionStatus(
                    externalExecutionId = "exec-1",
                    externalOrderId = "order-1",
                    stockId = "TQQQ",
                    stockName = "ProShares UltraPro QQQ",
                    createdAt = ZonedDateTime.parse("2026-06-01T16:00:00-04:00[America/New_York]"),
                    quantity = 1,
                    type = ExecutionTypeDto.PURCHASE,
                    reason = "no matching order submission",
                    observedAt = ZonedDateTime.parse("2026-06-02T10:00:00+09:00[Asia/Seoul]"),
                ),
            ),
        )
    }

    private class FakeTradingOperationsStatusPort(
        private val snapshot: TradingOperationsStatusSnapshot,
    ) : TradingOperationsStatusPort {
        override suspend fun loadStatus(): TradingOperationsStatusSnapshot {
            return snapshot
        }
    }

    private class FakeMarketServicePort(
        private val snapshots: Map<StockOrderMarket, AccountSnapshotDto> = emptyMap(),
        private val failures: Map<StockOrderMarket, RuntimeException> = emptyMap(),
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
            return BrokerOrderStatusDto(status = BrokerOrderStatus.UNKNOWN)
        }

        override fun findAccountSnapshot(query: AccountSnapshotQuery): AccountSnapshotDto {
            queries += query
            failures[query.market]?.let { throw it }
            return snapshots[query.market] ?: AccountSnapshotDto(
                market = query.market,
                exchange = query.exchange,
                currency = query.currency,
                positions = emptyList(),
            )
        }
    }
}

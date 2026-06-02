package com.example.stockpurchaseservice.adapter.out.api

import com.example.stockpurchaseservice.adapter.out.broker.BrokerAccountSnapshot
import com.example.stockpurchaseservice.adapter.out.broker.BrokerAccountSnapshotQuery
import com.example.stockpurchaseservice.adapter.out.broker.BrokerCancelableOrderItem
import com.example.stockpurchaseservice.adapter.out.broker.BrokerGateway
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderCancelCommand
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderCancelableQuery
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderCommand
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderHistoryItem
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderHistoryQuery
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.ExecutionLookupQuery
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class StockOrderAdapterExecutionIdentityTest {

    @Test
    fun `domestic execution lookup qualifies broker execution id with market`() {
        val gateway = FakeBrokerGateway(listOf(historyItem(externalExecutionId = "fill-1")))
        val adapter = DomesticStockOrderAdapter(gateway, isMockOrder = true)
        val query = ExecutionLookupQuery(
            from = ZonedDateTime.parse("2026-06-01T09:00:00+09:00"),
            to = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
        )

        val executions = adapter.findExecutionList(query)

        assertEquals("DOMESTIC:fill-1", executions.single().externalExecutionId)
        assertEquals(StockOrderMarket.DOMESTIC, gateway.historyQueries.single().market)
        assertEquals(query.from, gateway.historyQueries.single().from)
        assertEquals(query.to, gateway.historyQueries.single().to)
    }

    @Test
    fun `overseas execution lookup qualifies broker execution id with market`() {
        val gateway = FakeBrokerGateway(listOf(historyItem(externalExecutionId = "fill-1")))
        val adapter = OverseasStockOrderAdapter(gateway, isMockOrder = true)

        val executions = adapter.findExecutionList(
            ExecutionLookupQuery(
                from = ZonedDateTime.parse("2026-06-01T09:00:00+09:00"),
                to = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals("OVERSEAS_US:fill-1", executions.single().externalExecutionId)
        assertEquals(StockOrderMarket.OVERSEAS_US, gateway.historyQueries.single().market)
        assertEquals("", gateway.historyQueries.single().symbol)
    }

    @Test
    fun `status lookup qualifies recovered execution id with market`() {
        val gateway = FakeBrokerGateway(listOf(historyItem(externalExecutionId = "fill-1")))
        val adapter = OverseasStockOrderAdapter(gateway, isMockOrder = true)

        val status = adapter.findOrderSubmissionStatus(
            BrokerOrderStatusQuery(
                orderIntentId = UUID.randomUUID(),
                internalOrderId = UUID.randomUUID(),
                externalOrderId = "broker-1",
                symbol = "TQQQ",
                side = OrderIntentSide.BUY,
                market = StockOrderMarket.OVERSEAS_US,
            ),
        )

        assertEquals(BrokerOrderStatus.FILLED, status.status)
        assertEquals("OVERSEAS_US:fill-1", status.externalExecutionId)
    }

    @Test
    fun `status lookup qualifies deterministic fallback execution id with market`() {
        val gateway = FakeBrokerGateway(listOf(historyItem(externalExecutionId = null)))
        val adapter = DomesticStockOrderAdapter(gateway, isMockOrder = true)

        val status = adapter.findOrderSubmissionStatus(
            BrokerOrderStatusQuery(
                orderIntentId = UUID.randomUUID(),
                internalOrderId = UUID.randomUUID(),
                externalOrderId = "broker-1",
                symbol = "005930",
                side = OrderIntentSide.BUY,
                market = StockOrderMarket.DOMESTIC,
            ),
        )

        assertEquals(BrokerOrderStatus.FILLED, status.status)
        assertEquals("DOMESTIC:broker-1:1:PURCHASE", status.externalExecutionId)
    }

    private fun historyItem(externalExecutionId: String?): BrokerOrderHistoryItem {
        return BrokerOrderHistoryItem(
            externalOrderId = "broker-1",
            externalExecutionId = externalExecutionId,
            branchOrderNumber = null,
            symbol = "TQQQ",
            stockName = "TQQQ",
            orderedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            orderedQuantity = 1,
            orderedPrice = 100.0,
            cumulativeFilledQuantity = 1,
            remainingQuantity = 0,
            rejectedQuantity = 0,
            cancelledQuantity = 0,
            cancelled = false,
            side = OrderIntentSide.BUY,
            averageExecutionPrice = 100.0,
        )
    }

    private class FakeBrokerGateway(
        private val historyItems: List<BrokerOrderHistoryItem>,
    ) : BrokerGateway {
        val historyQueries: MutableList<BrokerOrderHistoryQuery> = mutableListOf()

        override fun submitOrder(command: BrokerOrderCommand): BrokerOrderSubmissionDto {
            error("not used")
        }

        override fun cancelOrder(command: BrokerOrderCancelCommand): BrokerOrderSubmissionDto {
            error("not used")
        }

        override fun findCancelableOrders(query: BrokerOrderCancelableQuery): List<BrokerCancelableOrderItem> {
            error("not used")
        }

        override fun findOrderHistory(query: BrokerOrderHistoryQuery): List<BrokerOrderHistoryItem> {
            historyQueries += query
            return historyItems
        }

        override fun findAccountSnapshot(query: BrokerAccountSnapshotQuery): BrokerAccountSnapshot {
            error("not used")
        }
    }
}

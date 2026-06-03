package com.example.stockpurchaseservice.adapter.out.api

import com.example.stockpurchaseservice.adapter.out.broker.BrokerGateway
import com.example.stockpurchaseservice.adapter.out.broker.BrokerAccountSnapshot
import com.example.stockpurchaseservice.adapter.out.broker.BrokerAccountSnapshotQuery
import com.example.stockpurchaseservice.adapter.out.broker.BrokerCancelableOrderItem
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderCancelCommand
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderCancelableQuery
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderCommand
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderHistoryItem
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderHistoryQuery
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.CancelOrderDto
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class StockOrderBrokerGatewayAdapterTest {

    @Test
    fun `overseas buy adapter submits normalized broker command`() {
        val brokerGateway = FakeBrokerGateway()
        val adapter = OverseasStockOrderAdapter(brokerGateway, isMockOrder = false)
        val order = PurchaseOrderDto(
            orderId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            stockId = "TQQQ",
            purchasePrice = 112.5,
            quantity = 3,
            market = StockOrderMarket.OVERSEAS_US,
            orderType = StockOrderType.LOC,
            exchange = "NYSE",
        )

        val result = adapter.buyStock(order)

        assertEquals("broker-1", result.externalOrderId)
        with(brokerGateway.submitted.single()) {
            assertEquals(order.orderId, internalOrderId)
            assertEquals(StockOrderMarket.OVERSEAS_US, market)
            assertEquals(OrderIntentSide.BUY, side)
            assertEquals("TQQQ", symbol)
            assertEquals("NYSE", exchange)
            assertEquals(StockOrderType.LOC, orderType)
            assertEquals(112.5, price)
            assertEquals(3, quantity)
            assertEquals(false, isMock)
        }
    }

    @Test
    fun `domestic execution lookup uses broker history query`() {
        val brokerGateway = FakeBrokerGateway()
        val adapter = DomesticStockOrderAdapter(brokerGateway, isMockOrder = true)

        adapter.findExecutionListAtOneDay()

        with(brokerGateway.historyQueries.single()) {
            assertEquals(StockOrderMarket.DOMESTIC, market)
            assertEquals(true, isMock)
        }
    }

    @Test
    fun `domestic cancel adapter submits normalized broker cancel command`() {
        val brokerGateway = FakeBrokerGateway()
        val adapter = DomesticStockOrderAdapter(brokerGateway, isMockOrder = false)
        val order = CancelOrderDto(
            orderId = UUID.fromString("00000000-0000-0000-0000-000000000021"),
            stockId = "005930",
            originalOrderId = "domestic-order-1",
            branchOrderNumber = "00001",
            quantity = 2,
            market = StockOrderMarket.DOMESTIC,
            orderType = StockOrderType.LIMIT,
            price = 0.0,
            cancelAll = true,
        )

        val result = adapter.cancelOrder(order)

        assertEquals("cancel-broker-1", result.externalOrderId)
        with(brokerGateway.cancelled.single()) {
            assertEquals(order.orderId, internalOrderId)
            assertEquals(StockOrderMarket.DOMESTIC, market)
            assertEquals("005930", symbol)
            assertEquals("domestic-order-1", originalOrderId)
            assertEquals("00001", branchOrderNumber)
            assertEquals(2, quantity)
            assertEquals(StockOrderType.LIMIT, orderType)
            assertEquals(0.0, price)
            assertEquals(true, cancelAll)
            assertEquals(false, isMock)
        }
    }

    @Test
    fun `overseas cancel adapter submits normalized broker cancel command`() {
        val brokerGateway = FakeBrokerGateway()
        val adapter = OverseasStockOrderAdapter(brokerGateway, isMockOrder = true)
        val order = CancelOrderDto(
            orderId = UUID.fromString("00000000-0000-0000-0000-000000000022"),
            stockId = "TQQQ",
            originalOrderId = "overseas-order-1",
            quantity = 3,
            market = StockOrderMarket.OVERSEAS_US,
            orderType = StockOrderType.LOC,
            price = 112.5,
            cancelAll = false,
            exchange = "AMEX",
        )

        adapter.cancelOrder(order)

        with(brokerGateway.cancelled.single()) {
            assertEquals(order.orderId, internalOrderId)
            assertEquals(StockOrderMarket.OVERSEAS_US, market)
            assertEquals("TQQQ", symbol)
            assertEquals("AMEX", exchange)
            assertEquals("overseas-order-1", originalOrderId)
            assertEquals(null, branchOrderNumber)
            assertEquals(3, quantity)
            assertEquals(StockOrderType.LOC, orderType)
            assertEquals(112.5, price)
            assertEquals(false, cancelAll)
            assertEquals(true, isMock)
        }
    }

    @Test
    fun `overseas status lookup queries by symbol and filters broker order id client side`() {
        val brokerGateway = FakeBrokerGateway()
        val adapter = OverseasStockOrderAdapter(brokerGateway, isMockOrder = false)
        val submittedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00")

        adapter.findOrderSubmissionStatus(
            BrokerOrderStatusQuery(
                orderIntentId = UUID.fromString("00000000-0000-0000-0000-000000000011"),
                internalOrderId = UUID.fromString("00000000-0000-0000-0000-000000000012"),
                externalOrderId = "broker-order-1",
                branchOrderNumber = "00009",
                symbol = "TQQQ",
                exchange = "NYSE",
                side = OrderIntentSide.BUY,
                market = StockOrderMarket.OVERSEAS_US,
                submittedAt = submittedAt,
            ),
        )

        val brokerSubmittedAt = submittedAt.withZoneSameInstant(BROKER_ZONE)
        with(brokerGateway.historyQueries.single()) {
            assertEquals(StockOrderMarket.OVERSEAS_US, market)
            assertEquals("TQQQ", symbol)
            assertEquals("NYSE", exchange)
            assertEquals("", externalOrderId)
            assertEquals("00009", branchOrderNumber)
            assertEquals(brokerSubmittedAt.minusDays(1), from)
            assertEquals(brokerSubmittedAt.plusDays(1), to)
            assertEquals(false, isMock)
        }
    }

    @Test
    fun `domestic status lookup uses configured broker history window`() {
        val brokerGateway = FakeBrokerGateway()
        val adapter = DomesticStockOrderAdapter(
            brokerGateway = brokerGateway,
            isMockOrder = true,
            statusLookupBackfillDays = 3,
            statusLookupForwardDays = 2,
        )
        val submittedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00")

        adapter.findOrderSubmissionStatus(
            BrokerOrderStatusQuery(
                orderIntentId = UUID.fromString("00000000-0000-0000-0000-000000000031"),
                internalOrderId = UUID.fromString("00000000-0000-0000-0000-000000000032"),
                externalOrderId = "domestic-order-1",
                branchOrderNumber = "00001",
                symbol = "005930",
                side = OrderIntentSide.BUY,
                market = StockOrderMarket.DOMESTIC,
                submittedAt = submittedAt,
            ),
        )

        val brokerSubmittedAt = submittedAt.withZoneSameInstant(BROKER_ZONE)
        with(brokerGateway.historyQueries.single()) {
            assertEquals(StockOrderMarket.DOMESTIC, market)
            assertEquals("", symbol)
            assertEquals("domestic-order-1", externalOrderId)
            assertEquals("00001", branchOrderNumber)
            assertEquals(brokerSubmittedAt.minusDays(3), from)
            assertEquals(brokerSubmittedAt.plusDays(2), to)
            assertEquals(true, isMock)
        }
    }

    @Test
    fun `domestic status lookup treats blank broker order id as absent`() {
        val brokerGateway = FakeBrokerGateway()
        val adapter = DomesticStockOrderAdapter(brokerGateway, isMockOrder = true)
        val submittedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00")

        adapter.findOrderSubmissionStatus(
            BrokerOrderStatusQuery(
                orderIntentId = UUID.fromString("00000000-0000-0000-0000-000000000041"),
                internalOrderId = UUID.fromString("00000000-0000-0000-0000-000000000042"),
                externalOrderId = " ",
                branchOrderNumber = " ",
                symbol = "005930",
                side = OrderIntentSide.BUY,
                market = StockOrderMarket.DOMESTIC,
                submittedAt = submittedAt,
            ),
        )

        with(brokerGateway.historyQueries.single()) {
            assertEquals(StockOrderMarket.DOMESTIC, market)
            assertEquals("005930", symbol)
            assertEquals("", externalOrderId)
            assertEquals(null, branchOrderNumber)
            assertEquals(true, isMock)
        }
    }

    @Test
    fun `overseas account snapshot passes exchange and currency to broker gateway`() {
        val brokerGateway = FakeBrokerGateway()
        val adapter = OverseasStockOrderAdapter(brokerGateway, isMockOrder = false)

        val snapshot = adapter.findAccountSnapshot(
            AccountSnapshotQuery(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NYSE",
                currency = "USD",
            ),
        )

        assertEquals(StockOrderMarket.OVERSEAS_US, snapshot.market)
        assertEquals("NYSE", snapshot.exchange)
        assertEquals("USD", snapshot.currency)
        assertEquals(1250.25, snapshot.availableCashAmount)
        assertEquals("USD", snapshot.cashCurrency)
        assertEquals(1250.25, snapshot.orderableCashAmount)
        assertEquals(1300.0, snapshot.settledCashAmount)
        assertEquals(1200.0, snapshot.withdrawableCashAmount)
        with(brokerGateway.accountSnapshotQueries.single()) {
            assertEquals(StockOrderMarket.OVERSEAS_US, market)
            assertEquals("NYSE", exchange)
            assertEquals("USD", currency)
            assertEquals(false, isMock)
        }
    }

    @Test
    fun `domestic account snapshot uses krx krw broker query`() {
        val brokerGateway = FakeBrokerGateway()
        val adapter = DomesticStockOrderAdapter(brokerGateway, isMockOrder = true)

        val snapshot = adapter.findAccountSnapshot(
            AccountSnapshotQuery(
                market = StockOrderMarket.DOMESTIC,
            ),
        )

        assertEquals(StockOrderMarket.DOMESTIC, snapshot.market)
        assertEquals("KRX", snapshot.exchange)
        assertEquals("KRW", snapshot.currency)
        assertEquals(1250.25, snapshot.availableCashAmount)
        assertEquals("KRW", snapshot.cashCurrency)
        assertEquals(1250.25, snapshot.orderableCashAmount)
        assertEquals(1300.0, snapshot.settledCashAmount)
        assertEquals(1200.0, snapshot.withdrawableCashAmount)
        with(brokerGateway.accountSnapshotQueries.single()) {
            assertEquals(StockOrderMarket.DOMESTIC, market)
            assertEquals("KRX", exchange)
            assertEquals("KRW", currency)
            assertEquals(true, isMock)
        }
    }

    private class FakeBrokerGateway : BrokerGateway {
        val submitted: MutableList<BrokerOrderCommand> = mutableListOf()
        val cancelled: MutableList<BrokerOrderCancelCommand> = mutableListOf()
        val historyQueries: MutableList<BrokerOrderHistoryQuery> = mutableListOf()
        val accountSnapshotQueries: MutableList<BrokerAccountSnapshotQuery> = mutableListOf()

        override fun submitOrder(command: BrokerOrderCommand): BrokerOrderSubmissionDto {
            submitted += command
            return BrokerOrderSubmissionDto(externalOrderId = "broker-1")
        }

        override fun cancelOrder(command: BrokerOrderCancelCommand): BrokerOrderSubmissionDto {
            cancelled += command
            return BrokerOrderSubmissionDto(externalOrderId = "cancel-broker-1")
        }

        override fun findCancelableOrders(query: BrokerOrderCancelableQuery): List<BrokerCancelableOrderItem> {
            error("not used")
        }

        override fun findOrderHistory(query: BrokerOrderHistoryQuery): List<BrokerOrderHistoryItem> {
            historyQueries += query
            return emptyList()
        }

        override fun findAccountSnapshot(query: BrokerAccountSnapshotQuery): BrokerAccountSnapshot {
            accountSnapshotQueries += query
            return BrokerAccountSnapshot(
                market = query.market,
                exchange = query.exchange,
                currency = query.currency,
                positions = emptyList(),
                availableCashAmount = 1250.25,
                cashCurrency = query.currency,
                orderableCashAmount = 1250.25,
                settledCashAmount = 1300.0,
                withdrawableCashAmount = 1200.0,
            )
        }
    }

    companion object {
        private val BROKER_ZONE = ZoneId.of("Asia/Seoul")
    }
}

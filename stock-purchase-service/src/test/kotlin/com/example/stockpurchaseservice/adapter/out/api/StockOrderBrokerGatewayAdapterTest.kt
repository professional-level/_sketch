package com.example.stockpurchaseservice.adapter.out.api

import com.example.stockpurchaseservice.adapter.out.broker.BrokerGateway
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderCommand
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderHistoryItem
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderHistoryQuery
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
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
        )

        val result = adapter.buyStock(order)

        assertEquals("broker-1", result.externalOrderId)
        with(brokerGateway.submitted.single()) {
            assertEquals(order.orderId, internalOrderId)
            assertEquals(StockOrderMarket.OVERSEAS_US, market)
            assertEquals(OrderIntentSide.BUY, side)
            assertEquals("TQQQ", symbol)
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

    private class FakeBrokerGateway : BrokerGateway {
        val submitted: MutableList<BrokerOrderCommand> = mutableListOf()
        val historyQueries: MutableList<BrokerOrderHistoryQuery> = mutableListOf()

        override fun submitOrder(command: BrokerOrderCommand): BrokerOrderSubmissionDto {
            submitted += command
            return BrokerOrderSubmissionDto(externalOrderId = "broker-1")
        }

        override fun findOrderHistory(query: BrokerOrderHistoryQuery): List<BrokerOrderHistoryItem> {
            historyQueries += query
            return emptyList()
        }
    }
}

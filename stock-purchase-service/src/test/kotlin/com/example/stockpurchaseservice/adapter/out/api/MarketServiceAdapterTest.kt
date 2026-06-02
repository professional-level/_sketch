package com.example.stockpurchaseservice.adapter.out.api

import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.CancelOrderDto
import com.example.stockpurchaseservice.application.port.out.DomesticStockOrderPort
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.ExecutionLookupQuery
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.OverseasStockOrderPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MarketServiceAdapterTest {

    @Test
    fun `routes buy order to overseas adapter`() {
        val domesticPort = FakeDomesticStockOrderPort()
        val overseasPort = FakeOverseasStockOrderPort()
        val adapter = MarketServiceAdapter(domesticPort, overseasPort)
        val order = PurchaseOrderDto(
            orderId = UUID.randomUUID(),
            stockId = "TQQQ",
            purchasePrice = 112.0,
            quantity = 3,
            market = StockOrderMarket.OVERSEAS_US,
        )

        adapter.buyStock(order)

        assertEquals(emptyList(), domesticPort.buyOrders)
        assertEquals(listOf(order), overseasPort.buyOrders)
    }

    @Test
    fun `combines broker executions from both market adapters`() {
        val domesticPort = FakeDomesticStockOrderPort(
            executions = listOf(execution("005930", "domestic-order")),
        )
        val overseasPort = FakeOverseasStockOrderPort(
            executions = listOf(execution("TQQQ", "overseas-order")),
        )
        val adapter = MarketServiceAdapter(domesticPort, overseasPort)

        val executions = adapter.findExecutionListAtOneDay()

        assertEquals(listOf("005930", "TQQQ"), executions.map { it.stockId })
    }

    @Test
    fun `forwards execution lookup range to both market adapters`() {
        val domesticPort = FakeDomesticStockOrderPort(
            executions = listOf(execution("005930", "domestic-order")),
        )
        val overseasPort = FakeOverseasStockOrderPort(
            executions = listOf(execution("TQQQ", "overseas-order")),
        )
        val adapter = MarketServiceAdapter(domesticPort, overseasPort)
        val query = ExecutionLookupQuery(
            from = ZonedDateTime.parse("2026-06-01T09:00:00+09:00"),
            to = ZonedDateTime.parse("2026-06-03T09:00:00+09:00"),
        )

        val executions = adapter.findExecutionList(query)

        assertEquals(listOf("005930", "TQQQ"), executions.map { it.stockId })
        assertEquals(listOf(query), domesticPort.executionQueries)
        assertEquals(listOf(query), overseasPort.executionQueries)
    }

    @Test
    fun `routes status lookup by market`() {
        val domesticPort = FakeDomesticStockOrderPort()
        val overseasPort = FakeOverseasStockOrderPort(
            status = BrokerOrderStatusDto(BrokerOrderStatus.SUBMITTED, externalOrderId = "broker-1"),
        )
        val adapter = MarketServiceAdapter(domesticPort, overseasPort)
        val query = BrokerOrderStatusQuery(
            orderIntentId = UUID.randomUUID(),
            internalOrderId = UUID.randomUUID(),
            externalOrderId = "broker-1",
            symbol = "TQQQ",
            side = OrderIntentSide.BUY,
            market = StockOrderMarket.OVERSEAS_US,
        )

        val status = adapter.findOrderSubmissionStatus(query)

        assertEquals(BrokerOrderStatus.SUBMITTED, status.status)
        assertEquals(listOf(query), overseasPort.statusQueries)
        assertEquals(emptyList(), domesticPort.statusQueries)
    }

    @Test
    fun `routes cancel order by market`() {
        val domesticPort = FakeDomesticStockOrderPort()
        val overseasPort = FakeOverseasStockOrderPort()
        val adapter = MarketServiceAdapter(domesticPort, overseasPort)
        val order = CancelOrderDto(
            orderId = UUID.randomUUID(),
            stockId = "TQQQ",
            originalOrderId = "broker-1",
            quantity = 1,
            market = StockOrderMarket.OVERSEAS_US,
        )

        adapter.cancelOrder(order)

        assertEquals(emptyList(), domesticPort.cancelOrders)
        assertEquals(listOf(order), overseasPort.cancelOrders)
    }

    private fun execution(stockId: String, externalOrderId: String): ExecutedStockDto {
        return ExecutedStockDto(
            stockId = stockId,
            stockName = stockId,
            createdAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            quantity = 1,
            type = ExecutionTypeDto.PURCHASE,
            externalOrderId = externalOrderId,
            externalExecutionId = "$externalOrderId:1",
        )
    }

    private class FakeDomesticStockOrderPort(
        private val executions: List<ExecutedStockDto> = emptyList(),
        private val status: BrokerOrderStatusDto = BrokerOrderStatusDto(BrokerOrderStatus.UNKNOWN),
    ) : DomesticStockOrderPort {
        val buyOrders: MutableList<PurchaseOrderDto> = mutableListOf()
        val cancelOrders: MutableList<CancelOrderDto> = mutableListOf()
        val executionQueries: MutableList<ExecutionLookupQuery> = mutableListOf()
        val statusQueries: MutableList<BrokerOrderStatusQuery> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            buyOrders += order
            return BrokerOrderSubmissionDto(order.orderId.toString())
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto(order.orderId.toString())
        }

        override fun cancelOrder(order: CancelOrderDto): BrokerOrderSubmissionDto {
            cancelOrders += order
            return BrokerOrderSubmissionDto(order.orderId.toString())
        }

        override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
            return executions
        }

        override fun findExecutionList(query: ExecutionLookupQuery): List<ExecutedStockDto> {
            executionQueries += query
            return executions
        }

        override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
            statusQueries += query
            return status
        }
    }

    private class FakeOverseasStockOrderPort(
        private val executions: List<ExecutedStockDto> = emptyList(),
        private val status: BrokerOrderStatusDto = BrokerOrderStatusDto(BrokerOrderStatus.UNKNOWN),
    ) : OverseasStockOrderPort {
        val buyOrders: MutableList<PurchaseOrderDto> = mutableListOf()
        val cancelOrders: MutableList<CancelOrderDto> = mutableListOf()
        val executionQueries: MutableList<ExecutionLookupQuery> = mutableListOf()
        val statusQueries: MutableList<BrokerOrderStatusQuery> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            buyOrders += order
            return BrokerOrderSubmissionDto(order.orderId.toString())
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto(order.orderId.toString())
        }

        override fun cancelOrder(order: CancelOrderDto): BrokerOrderSubmissionDto {
            cancelOrders += order
            return BrokerOrderSubmissionDto(order.orderId.toString())
        }

        override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
            return executions
        }

        override fun findExecutionList(query: ExecutionLookupQuery): List<ExecutedStockDto> {
            executionQueries += query
            return executions
        }

        override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
            statusQueries += query
            return status
        }
    }
}

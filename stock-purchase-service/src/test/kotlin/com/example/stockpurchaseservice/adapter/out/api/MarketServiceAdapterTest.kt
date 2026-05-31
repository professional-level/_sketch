package com.example.stockpurchaseservice.adapter.out.api

import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.DomesticStockOrderPort
import com.example.stockpurchaseservice.application.port.out.OverseasStockOrderPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
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

    private class FakeDomesticStockOrderPort : DomesticStockOrderPort {
        val buyOrders: MutableList<PurchaseOrderDto> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            buyOrders += order
            return BrokerOrderSubmissionDto(order.orderId.toString())
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto(order.orderId.toString())
        }
    }

    private class FakeOverseasStockOrderPort : OverseasStockOrderPort {
        val buyOrders: MutableList<PurchaseOrderDto> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            buyOrders += order
            return BrokerOrderSubmissionDto(order.orderId.toString())
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto(order.orderId.toString())
        }
    }
}

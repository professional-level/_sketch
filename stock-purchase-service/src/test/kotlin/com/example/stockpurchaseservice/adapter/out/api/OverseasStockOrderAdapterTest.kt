package com.example.stockpurchaseservice.adapter.out.api

import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OverseasStockOrderAdapterTest {

    @Test
    fun `builds real us overseas buy request using loc order division`() {
        val order = PurchaseOrderDto(
            orderId = UUID.randomUUID(),
            stockId = "tqqq",
            purchasePrice = 112.5,
            quantity = 3,
            market = StockOrderMarket.OVERSEAS_US,
            orderType = StockOrderType.LOC,
        )

        val body = order.toUsOverseasBuyRequest(isMock = false)

        assertEquals("TQQQ", body["PDNO"])
        assertEquals("NASD", body["OVRS_EXCG_CD"])
        assertEquals(3, body["ORD_QTY"])
        assertEquals("112.5", body["OVRS_ORD_UNPR"])
        assertEquals("34", body["ORD_DVSN"])
        assertEquals("0", body["ORD_SVR_DVSN_CD"])
        assertEquals(false, body["isMock"])
    }

    @Test
    fun `builds mock us overseas buy request using limit-only mock order division`() {
        val order = PurchaseOrderDto(
            orderId = UUID.randomUUID(),
            stockId = "SOXL",
            purchasePrice = 24.0,
            quantity = 10,
            market = StockOrderMarket.OVERSEAS_US,
            orderType = StockOrderType.LOC,
        )

        val body = order.toUsOverseasBuyRequest(isMock = true)

        assertEquals("SOXL", body["PDNO"])
        assertEquals("NASD", body["OVRS_EXCG_CD"])
        assertEquals("24", body["OVRS_ORD_UNPR"])
        assertEquals("00", body["ORD_DVSN"])
        assertEquals(true, body["isMock"])
    }

    @Test
    fun `builds real us overseas sell request using moc order division`() {
        val order = SellingOrderDto(
            orderId = UUID.randomUUID(),
            stockId = "TQQQ",
            sellingPrice = 0.0,
            quantity = 2,
            market = StockOrderMarket.OVERSEAS_US,
            orderType = StockOrderType.MOC,
        )

        val body = order.toUsOverseasSellRequest(isMock = false)

        assertEquals("TQQQ", body["PDNO"])
        assertEquals("NASD", body["OVRS_EXCG_CD"])
        assertEquals(2, body["ORD_QTY"])
        assertEquals("0", body["OVRS_ORD_UNPR"])
        assertEquals("33", body["ORD_DVSN"])
        assertEquals("00", body["SLL_TYPE"])
    }
}

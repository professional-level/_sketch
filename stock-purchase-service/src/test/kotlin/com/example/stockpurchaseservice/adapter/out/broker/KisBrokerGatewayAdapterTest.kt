package com.example.stockpurchaseservice.adapter.out.broker

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class KisBrokerGatewayAdapterTest {

    @Test
    fun `builds real us overseas buy request using loc order division`() {
        val command = brokerCommand(
            side = OrderIntentSide.BUY,
            symbol = "tqqq",
            price = 112.5,
            quantity = 3,
            orderType = StockOrderType.LOC,
            isMock = false,
        )

        val body = command.toKisUsOverseasOrderRequest()

        assertEquals("TQQQ", body["PDNO"])
        assertEquals("NASD", body["OVRS_EXCG_CD"])
        assertEquals(3, body["ORD_QTY"])
        assertEquals("112.5", body["OVRS_ORD_UNPR"])
        assertEquals("34", body["ORD_DVSN"])
        assertEquals(null, body["SLL_TYPE"])
        assertEquals("0", body["ORD_SVR_DVSN_CD"])
        assertEquals(false, body["isMock"])
    }

    @Test
    fun `builds mock us overseas buy request using limit-only mock order division`() {
        val command = brokerCommand(
            side = OrderIntentSide.BUY,
            symbol = "SOXL",
            price = 24.0,
            quantity = 10,
            orderType = StockOrderType.LOC,
            isMock = true,
        )

        val body = command.toKisUsOverseasOrderRequest()

        assertEquals("SOXL", body["PDNO"])
        assertEquals("NASD", body["OVRS_EXCG_CD"])
        assertEquals("24", body["OVRS_ORD_UNPR"])
        assertEquals("00", body["ORD_DVSN"])
        assertEquals(true, body["isMock"])
    }

    @Test
    fun `builds real us overseas sell request using moc order division`() {
        val command = brokerCommand(
            side = OrderIntentSide.SELL,
            symbol = "TQQQ",
            price = 0.0,
            quantity = 2,
            orderType = StockOrderType.MOC,
            isMock = false,
        )

        val body = command.toKisUsOverseasOrderRequest()

        assertEquals("TQQQ", body["PDNO"])
        assertEquals("NASD", body["OVRS_EXCG_CD"])
        assertEquals(2, body["ORD_QTY"])
        assertEquals("0", body["OVRS_ORD_UNPR"])
        assertEquals("33", body["ORD_DVSN"])
        assertEquals("00", body["SLL_TYPE"])
    }

    private fun brokerCommand(
        side: OrderIntentSide,
        symbol: String,
        price: Double,
        quantity: Int,
        orderType: StockOrderType,
        isMock: Boolean,
    ): BrokerOrderCommand {
        return BrokerOrderCommand(
            internalOrderId = UUID.randomUUID(),
            market = StockOrderMarket.OVERSEAS_US,
            side = side,
            symbol = symbol,
            orderType = orderType,
            price = price,
            quantity = quantity,
            isMock = isMock,
        )
    }
}

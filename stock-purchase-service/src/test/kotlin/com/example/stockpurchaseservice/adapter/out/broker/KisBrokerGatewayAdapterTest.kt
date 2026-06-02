package com.example.stockpurchaseservice.adapter.out.broker

import ApiResponse
import DailyExecutionOrdersResponseOuterClass
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderQueryFailedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderRejectedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionUnknownException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderTemporaryUnavailableException
import com.example.stockpurchaseservice.application.port.out.ExecutionQuantityModeDto
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import com.example.stockpurchaseservice.config.broker.KisBrokerGatewayProperties
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import java.time.ZonedDateTime
import java.util.UUID
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KisBrokerGatewayAdapterTest {

    @Test
    fun `builds real us overseas buy request using loc order division`() {
        val command = brokerCommand(
            side = OrderIntentSide.BUY,
            symbol = "tqqq",
            exchange = "NYSE",
            price = 112.5,
            quantity = 3,
            orderType = StockOrderType.LOC,
            isMock = false,
        )

        val body = command.toKisUsOverseasOrderRequest()

        assertEquals("TQQQ", body["PDNO"])
        assertEquals("NYSE", body["OVRS_EXCG_CD"])
        assertEquals(3, body["ORD_QTY"])
        assertEquals("112.5", body["OVRS_ORD_UNPR"])
        assertEquals("34", body["ORD_DVSN"])
        assertEquals(null, body["SLL_TYPE"])
        assertEquals("0", body["ORD_SVR_DVSN_CD"])
        assertEquals(false, body["isMock"])
    }

    @Test
    fun `builds real us overseas buy request using moc order division`() {
        val command = brokerCommand(
            side = OrderIntentSide.BUY,
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
        assertEquals("32", body["ORD_DVSN"])
        assertEquals(null, body["SLL_TYPE"])
        assertEquals("0", body["ORD_SVR_DVSN_CD"])
        assertEquals(false, body["isMock"])
    }

    @Test
    fun `builds us overseas cancel request using configured exchange code`() {
        val command = brokerCancelCommand(
            market = StockOrderMarket.OVERSEAS_US,
            symbol = "BRK.B",
            exchange = "NYSE",
            originalOrderId = "overseas-order-1",
            branchOrderNumber = null,
            price = 450.25,
            quantity = 1,
            orderType = StockOrderType.LIMIT,
            cancelAll = true,
            isMock = false,
        )

        val body = command.toKisUsOverseasCancelRequest()

        assertEquals("NYSE", body["OVRS_EXCG_CD"])
        assertEquals("BRK.B", body["PDNO"])
        assertEquals("overseas-order-1", body["ORGN_ODNO"])
        assertEquals("02", body["RVSE_CNCL_DVSN_CD"])
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

    @Test
    fun `builds domestic cancel request using original broker order fields`() {
        val command = brokerCancelCommand(
            market = StockOrderMarket.DOMESTIC,
            symbol = "005930",
            originalOrderId = "domestic-order-1",
            branchOrderNumber = "00001",
            price = 0.0,
            quantity = 2,
            orderType = StockOrderType.LIMIT,
            cancelAll = true,
            isMock = false,
        )

        val body = command.toKisDomesticCancelRequest()

        assertEquals("00001", body["KRX_FWDG_ORD_ORGNO"])
        assertEquals("domestic-order-1", body["ORGN_ODNO"])
        assertEquals("00", body["ORD_DVSN"])
        assertEquals("02", body["RVSE_CNCL_DVSN_CD"])
        assertEquals(2, body["ORD_QTY"])
        assertEquals(0L, body["ORD_UNPR"])
        assertEquals("Y", body["QTY_ALL_ORD_YN"])
        assertEquals("KRX", body["EXCG_ID_DVSN_CD"])
        assertEquals(false, body["isMock"])
    }

    @Test
    fun `domestic cancel request requires branch order number`() {
        val command = brokerCancelCommand(
            market = StockOrderMarket.DOMESTIC,
            symbol = "005930",
            originalOrderId = "domestic-order-1",
            branchOrderNumber = null,
            price = 0.0,
            quantity = 2,
            orderType = StockOrderType.LIMIT,
            isMock = false,
        )

        assertFailsWith<IllegalArgumentException> {
            command.toKisDomesticCancelRequest()
        }
    }

    @Test
    fun `builds overseas cancel request using original broker order fields`() {
        val command = brokerCancelCommand(
            market = StockOrderMarket.OVERSEAS_US,
            symbol = "tqqq",
            originalOrderId = "overseas-order-1",
            branchOrderNumber = null,
            price = 112.5,
            quantity = 3,
            orderType = StockOrderType.LOC,
            cancelAll = false,
            isMock = true,
        )

        val body = command.toKisUsOverseasCancelRequest()

        assertEquals("NASD", body["OVRS_EXCG_CD"])
        assertEquals("TQQQ", body["PDNO"])
        assertEquals("overseas-order-1", body["ORGN_ODNO"])
        assertEquals("02", body["RVSE_CNCL_DVSN_CD"])
        assertEquals(3, body["ORD_QTY"])
        assertEquals("112.5", body["OVRS_ORD_UNPR"])
        assertEquals("", body["MGCO_APTM_ODNO"])
        assertEquals("0", body["ORD_SVR_DVSN_CD"])
        assertEquals(true, body["isMock"])
    }

    @Test
    fun `overseas cancel checks unfilled orders before submitting cancel request`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk200": "",
                      "ctx_area_nk200": "",
                      "output": [
                        {
                          "odno": "overseas-order-1",
                          "pdno": "TQQQ",
                          "prdt_name": "ProShares UltraPro QQQ",
                          "ord_dt": "20260602",
                          "ord_tmd": "093000",
                          "ft_ord_qty": "3",
                          "ft_ccld_qty": "1",
                          "nccs_qty": "2",
                          "sll_buy_dvsn_cd": "02"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("overseas-cancel-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = "TQQQ",
                originalOrderId = "overseas-order-1",
                branchOrderNumber = null,
                price = 112.5,
                quantity = 2,
                orderType = StockOrderType.LOC,
                isMock = false,
            ),
        )

        assertEquals("overseas-cancel-1", submission.externalOrderId)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("/open-api/overseas/trading/inquire-nccs", exchangeFunction.requests[0].url().path)
        assertEquals("NASD", exchangeFunction.requests[0].queryValue("ovrsExcgCd"))
        assertEquals("DS", exchangeFunction.requests[0].queryValue("sortSqn"))
        assertEquals("/open-api/overseas/trading/order-rvsecncl", exchangeFunction.requests[1].url().path)
    }

    @Test
    fun `overseas cancel matches unfilled order by original broker order id`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk200": "",
                      "ctx_area_nk200": "",
                      "output": [
                        {
                          "odno": "overseas-revised-order-1",
                          "orgn_odno": "overseas-order-1",
                          "pdno": "TQQQ",
                          "prdt_name": "ProShares UltraPro QQQ",
                          "ord_dt": "20260602",
                          "ord_tmd": "093000",
                          "ft_ord_qty": "3",
                          "ft_ccld_qty": "1",
                          "nccs_qty": "2",
                          "sll_buy_dvsn_cd": "02"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("overseas-cancel-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = "TQQQ",
                originalOrderId = "overseas-order-1",
                branchOrderNumber = null,
                price = 112.5,
                quantity = 2,
                orderType = StockOrderType.LOC,
                isMock = false,
            ),
        )

        assertEquals("overseas-cancel-1", submission.externalOrderId)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("/open-api/overseas/trading/inquire-nccs", exchangeFunction.requests[0].url().path)
        assertEquals("/open-api/overseas/trading/order-rvsecncl", exchangeFunction.requests[1].url().path)
    }

    @Test
    fun `overseas cancel selects highest possible quantity from matching unfilled rows`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk200": "",
                      "ctx_area_nk200": "",
                      "output": [
                        {
                          "odno": "overseas-order-1",
                          "pdno": "TQQQ",
                          "nccs_qty": "0"
                        },
                        {
                          "odno": "overseas-revised-order-1",
                          "orgn_odno": "overseas-order-1",
                          "pdno": "TQQQ",
                          "nccs_qty": "2"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("overseas-cancel-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = "TQQQ",
                originalOrderId = "overseas-order-1",
                branchOrderNumber = null,
                price = 112.5,
                quantity = 2,
                orderType = StockOrderType.LOC,
                isMock = false,
            ),
        )

        assertEquals("overseas-cancel-1", submission.externalOrderId)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("/open-api/overseas/trading/order-rvsecncl", exchangeFunction.requests[1].url().path)
    }

    @Test
    fun `overseas cancel uses branch order number to disambiguate duplicate broker order ids`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk200": "",
                      "ctx_area_nk200": "",
                      "output": [
                        {
                          "odno": "overseas-order-1",
                          "ord_gno_brno": "99999",
                          "pdno": "TQQQ",
                          "ord_dt": "20260602",
                          "ord_tmd": "093000",
                          "ft_ord_qty": "3",
                          "ft_ccld_qty": "2",
                          "nccs_qty": "1",
                          "sll_buy_dvsn_cd": "02"
                        },
                        {
                          "odno": "overseas-order-1",
                          "ORD_GNO_BRNO": "00001",
                          "pdno": "TQQQ",
                          "ord_dt": "20260602",
                          "ord_tmd": "093100",
                          "ft_ord_qty": "3",
                          "ft_ccld_qty": "1",
                          "nccs_qty": "2",
                          "sll_buy_dvsn_cd": "02"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("overseas-cancel-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = "TQQQ",
                originalOrderId = "overseas-order-1",
                branchOrderNumber = "00001",
                price = 112.5,
                quantity = 2,
                orderType = StockOrderType.LOC,
                isMock = false,
            ),
        )

        assertEquals("overseas-cancel-1", submission.externalOrderId)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("/open-api/overseas/trading/order-rvsecncl", exchangeFunction.requests[1].url().path)
    }

    @Test
    fun `overseas unfilled lookup maps uppercase aliases and cursor pagination`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "RT_CD": "0",
                      "CTX_AREA_FK200": "FK1",
                      "CTX_AREA_NK200": "NK1",
                      "OUTPUT": []
                    }
                    """.trimIndent(),
                ),
                jsonResponse(
                    """
                    {
                      "RT_CD": "0",
                      "CTX_AREA_FK200": "",
                      "CTX_AREA_NK200": "",
                      "OUTPUT": [
                        {
                          "ODNO": "overseas-order-1",
                          "ORD_GNO_BRNO": "00001",
                          "OVRS_PDNO": "TQQQ",
                          "NCCS_QTY": "2"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("overseas-cancel-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = "TQQQ",
                originalOrderId = "overseas-order-1",
                branchOrderNumber = "00001",
                price = 112.5,
                quantity = 2,
                orderType = StockOrderType.LOC,
                isMock = false,
            ),
        )

        assertEquals("overseas-cancel-1", submission.externalOrderId)
        assertEquals(3, exchangeFunction.requests.size)
        assertEquals("/open-api/overseas/trading/inquire-nccs", exchangeFunction.requests[0].url().path)
        assertEquals("", exchangeFunction.requests[0].queryValue("ctxAreaFk200"))
        assertEquals("", exchangeFunction.requests[0].queryValue("ctxAreaNk200"))
        assertEquals("/open-api/overseas/trading/inquire-nccs", exchangeFunction.requests[1].url().path)
        assertEquals("FK1", exchangeFunction.requests[1].queryValue("ctxAreaFk200"))
        assertEquals("NK1", exchangeFunction.requests[1].queryValue("ctxAreaNk200"))
        assertEquals("/open-api/overseas/trading/order-rvsecncl", exchangeFunction.requests[2].url().path)
    }

    @Test
    fun `finds overseas cancelable orders from unfilled lookup without submitting cancel`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk200": "",
                      "ctx_area_nk200": "",
                      "output": [
                        {
                          "ODNO": "other-order",
                          "OVRS_PDNO": "SOXL",
                          "NCCS_QTY": "1"
                        },
                        {
                          "ODNO": "overseas-order-1",
                          "ORD_GNO_BRNO": "00001",
                          "OVRS_PDNO": "TQQQ",
                          "NCCS_QTY": "2"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val items = adapter.findCancelableOrders(
            BrokerOrderCancelableQuery(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = "TQQQ",
                exchange = "NASD",
                originalOrderId = "overseas-order-1",
                branchOrderNumber = "00001",
                isMock = false,
            ),
        )

        assertEquals(1, items.size)
        with(items.single()) {
            assertEquals("overseas-order-1", orderId)
            assertEquals("00001", branchOrderNumber)
            assertEquals("TQQQ", symbol)
            assertEquals(2L, possibleQuantity)
        }
        assertEquals(1, exchangeFunction.requests.size)
        assertEquals("/open-api/overseas/trading/inquire-nccs", exchangeFunction.requests.single().url().path)
    }

    @Test
    fun `finds overseas cancelable order by original order number alias`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk200": "",
                      "ctx_area_nk200": "",
                      "output": [
                        {
                          "ordNo": "overseas-revised-order-1",
                          "orgnOrdNo": "overseas-order-1",
                          "ordGnoBrno": "00001",
                          "ovrsPdno": "TQQQ",
                          "nccsQty": "2"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val items = adapter.findCancelableOrders(
            BrokerOrderCancelableQuery(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = "TQQQ",
                exchange = "NASD",
                originalOrderId = "overseas-order-1",
                branchOrderNumber = "00001",
                isMock = false,
            ),
        )

        val item = items.single()
        assertEquals("overseas-revised-order-1", item.orderId)
        assertEquals("overseas-order-1", item.originalOrderId)
        assertEquals("00001", item.branchOrderNumber)
        assertEquals(2L, item.possibleQuantity)
    }

    @Test
    fun `overseas cancel rejects when explicit branch order number does not match unfilled order`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk200": "",
                      "ctx_area_nk200": "",
                      "output": [
                        {
                          "odno": "overseas-order-1",
                          "ord_gno_brno": "99999",
                          "pdno": "TQQQ",
                          "ord_dt": "20260602",
                          "ord_tmd": "093000",
                          "ft_ord_qty": "3",
                          "ft_ccld_qty": "1",
                          "nccs_qty": "2",
                          "sll_buy_dvsn_cd": "02"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderRejectedException> {
            adapter.cancelOrder(
                brokerCancelCommand(
                    market = StockOrderMarket.OVERSEAS_US,
                    symbol = "TQQQ",
                    originalOrderId = "overseas-order-1",
                    branchOrderNumber = "00001",
                    price = 112.5,
                    quantity = 2,
                    orderType = StockOrderType.LOC,
                    isMock = false,
                ),
            )
        }

        assertEquals("overseas order is not cancelable: overseas-order-1", exception.message)
        assertEquals(1, exchangeFunction.requests.size)
    }

    @Test
    fun `overseas cancel rejects when order is not found in unfilled orders`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk200": "",
                      "ctx_area_nk200": "",
                      "output": []
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderRejectedException> {
            adapter.cancelOrder(
                brokerCancelCommand(
                    market = StockOrderMarket.OVERSEAS_US,
                    symbol = "TQQQ",
                    originalOrderId = "missing-overseas-order",
                    branchOrderNumber = null,
                    price = 112.5,
                    quantity = 1,
                    orderType = StockOrderType.LOC,
                    isMock = false,
                ),
            )
        }

        assertEquals("overseas order is not cancelable: missing-overseas-order", exception.message)
        assertEquals(1, exchangeFunction.requests.size)
    }

    @Test
    fun `overseas cancel rejects when remaining quantity is lower than requested quantity`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk200": "",
                      "ctx_area_nk200": "",
                      "output": [
                        {
                          "odno": "overseas-order-1",
                          "pdno": "TQQQ",
                          "ord_dt": "20260602",
                          "ord_tmd": "093000",
                          "ft_ord_qty": "3",
                          "ft_ccld_qty": "2",
                          "nccs_qty": "1",
                          "sll_buy_dvsn_cd": "02"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderRejectedException> {
            adapter.cancelOrder(
                brokerCancelCommand(
                    market = StockOrderMarket.OVERSEAS_US,
                    symbol = "TQQQ",
                    originalOrderId = "overseas-order-1",
                    branchOrderNumber = null,
                    price = 112.5,
                    quantity = 2,
                    orderType = StockOrderType.LOC,
                    isMock = false,
                ),
            )
        }

        assertEquals(
            "overseas order cancel quantity exceeds remaining quantity: requested=2 remaining=1",
            exception.message,
        )
        assertEquals(1, exchangeFunction.requests.size)
    }

    @Test
    fun `overseas order history follows kis pagination cursor`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "rt_cd": "0",
                  "ctx_area_fk200": "FK1",
                  "ctx_area_nk200": "NK1",
                  "output": [
                    {
                      "odno": "order-1",
                      "pdno": "TQQQ",
                      "prdt_name": "ProShares UltraPro QQQ",
                      "ord_dt": "20260601",
                      "ord_tmd": "093000",
                      "ft_ord_qty": "3",
                      "ft_ccld_qty": "1",
                      "nccs_qty": "2",
                      "sll_buy_dvsn_cd": "02",
                      "ft_ccld_unpr3": "112.5"
                    }
                  ]
                }
                """.trimIndent(),
                """
                {
                  "rt_cd": "0",
                  "ctx_area_fk200": "",
                  "ctx_area_nk200": "",
                  "output": [
                    {
                      "odno": "order-2",
                      "pdno": "TQQQ",
                      "prdt_name": "ProShares UltraPro QQQ",
                      "ord_dt": "20260602",
                      "ord_tmd": "093100",
                      "ft_ord_qty": "3",
                      "ft_ccld_qty": "3",
                      "nccs_qty": "0",
                      "sll_buy_dvsn_cd": "02",
                      "ft_ccld_unpr3": "111.0"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val items = adapter.findOrderHistory(
            BrokerOrderHistoryQuery(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = "TQQQ",
                from = ZonedDateTime.parse("2026-06-01T09:00:00+09:00"),
                to = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
                isMock = false,
            ),
        )

        assertEquals(listOf("order-1", "order-2"), items.map { it.externalOrderId })
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("20260601", exchangeFunction.requests[0].queryValue("ordStrtDt"))
        assertEquals("20260602", exchangeFunction.requests[0].queryValue("ordEndDt"))
        assertEquals("FK1", exchangeFunction.requests[1].queryValue("ctxAreaFk200"))
        assertEquals("NK1", exchangeFunction.requests[1].queryValue("ctxAreaNk200"))
    }

    @Test
    fun `overseas order history sends configured exchange code`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "rt_cd": "0",
                  "ctx_area_fk200": "",
                  "ctx_area_nk200": "",
                  "output": []
                }
                """.trimIndent(),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        adapter.findOrderHistory(
            historyQuery().copy(exchange = "NYSE"),
        )

        assertEquals("NYSE", exchangeFunction.requests.single().queryValue("ovrsExcgCd"))
    }

    @Test
    fun `overseas order history maps uppercase top level aliases and cursor`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "RT_CD": "0",
                  "CTX_AREA_FK200": "FKU",
                  "CTX_AREA_NK200": "NKU",
                  "OUTPUT": [
                    {
                      "ODNO": "upper-order-1",
                      "PDNO": "TQQQ",
                      "PRDT_ENG_NAME": "ProShares UltraPro QQQ",
                      "ORD_DT": "20260601",
                      "THCO_ORD_TMD": "093000",
                      "ORD_QTY": "3",
                      "TOT_CCLD_QTY": "1",
                      "RMN_QTY": "2",
                      "SLL_BUY_DVSN_NAME": "BUY",
                      "AVG_PRVS": "112.5"
                    }
                  ]
                }
                """.trimIndent(),
                """
                {
                  "RT_CD": "0",
                  "CTX_AREA_FK200": "",
                  "CTX_AREA_NK200": "",
                  "OUTPUT": {
                    "ODNO": "upper-order-2",
                    "PDNO": "TQQQ",
                    "ORD_DT": "20260602",
                    "THCO_ORD_TMD": "093100",
                    "ORD_QTY": "3",
                    "TOT_CCLD_QTY": "3",
                    "RMN_QTY": "0",
                    "SLL_BUY_DVSN_NAME": "BUY",
                    "AVG_PRVS": "111.0"
                  }
                }
                """.trimIndent(),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val items = adapter.findOrderHistory(historyQuery())

        assertEquals(listOf("upper-order-1", "upper-order-2"), items.map { it.externalOrderId })
        assertEquals(listOf(1L, 3L), items.map { it.cumulativeFilledQuantity })
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("FKU", exchangeFunction.requests[1].queryValue("ctxAreaFk200"))
        assertEquals("NKU", exchangeFunction.requests[1].queryValue("ctxAreaNk200"))
    }

    @Test
    fun `overseas order history maps output1 and official side name alias`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "rt_cd": "0",
                  "ctxAreaFk200": "FK_CAMEL",
                  "ctxAreaNk200": "NK_CAMEL",
                  "output1": {
                    "odno": "output1-order-1",
                    "pdno": "TQQQ",
                    "prdt_name": "ProShares UltraPro QQQ",
                    "ord_dt": "20260601",
                    "ord_tmd": "093000",
                    "ft_ord_qty": "3",
                    "ft_ccld_qty": "1",
                    "nccs_qty": "2",
                    "sll_buy_dvsn_cd_name": "매도",
                    "ft_ccld_unpr3": "112.5"
                  }
                }
                """.trimIndent(),
                """
                {
                  "rt_cd": "0",
                  "ctxAreaFk200": "",
                  "ctxAreaNk200": "",
                  "output1": [
                    {
                      "odno": "output1-order-2",
                      "pdno": "TQQQ",
                      "prdt_name": "ProShares UltraPro QQQ",
                      "ord_dt": "20260602",
                      "ord_tmd": "093100",
                      "ft_ord_qty": "3",
                      "ft_ccld_qty": "3",
                      "nccs_qty": "0",
                      "sll_buy_dvsn_cd_name": "매수",
                      "ft_ccld_unpr3": "111.0"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val items = adapter.findOrderHistory(historyQuery())

        assertEquals(listOf("output1-order-1", "output1-order-2"), items.map { it.externalOrderId })
        assertEquals(listOf(OrderIntentSide.SELL, OrderIntentSide.BUY), items.map { it.side })
        assertEquals(ExecutionTypeDto.SELLING, items.first().toExecutionDto()?.type)
        assertEquals(ExecutionTypeDto.PURCHASE, items.last().toExecutionDto()?.type)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("FK_CAMEL", exchangeFunction.requests[1].queryValue("ctxAreaFk200"))
        assertEquals("NK_CAMEL", exchangeFunction.requests[1].queryValue("ctxAreaNk200"))
    }

    @Test
    fun `overseas account snapshot maps balance positions and summary`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "rt_cd": "0",
                  "ctx_area_fk200": "",
                  "ctx_area_nk200": "",
                  "output1": [
                    {
                      "ovrs_pdno": "TQQQ",
                      "ovrs_item_name": "ProShares UltraPro QQQ",
                      "ovrs_cblc_qty": "3",
                      "pchs_avg_pric": "112.5",
                      "now_pric2": "120.0",
                      "frcr_pchs_amt1": "337.5",
                      "ovrs_stck_evlu_amt": "360.0",
                      "frcr_evlu_pfls_amt": "22.5"
                    }
                  ],
                  "output2": {
                    "ovrs_ord_psbl_amt": "1250.25",
                    "frcr_dncl_amt_2": "1300.00",
                    "frcr_wdrw_psbl_amt": "1200.50",
                    "frcr_buy_amt_smtl": "337.5",
                    "tot_evlu_amt": "360.0",
                    "tot_evlu_pfls_amt": "22.5"
                  }
                }
                """.trimIndent(),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val snapshot = adapter.findAccountSnapshot(
            BrokerAccountSnapshotQuery(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                isMock = false,
            ),
        )

        assertEquals(StockOrderMarket.OVERSEAS_US, snapshot.market)
        assertEquals("NASD", snapshot.exchange)
        assertEquals("USD", snapshot.currency)
        assertEquals(1250.25, snapshot.availableCashAmount)
        assertEquals("USD", snapshot.cashCurrency)
        assertEquals(1250.25, snapshot.orderableCashAmount)
        assertEquals(1300.0, snapshot.settledCashAmount)
        assertEquals(1200.5, snapshot.withdrawableCashAmount)
        assertEquals(337.5, snapshot.totalPurchaseAmount)
        assertEquals(360.0, snapshot.totalEvaluationAmount)
        assertEquals(22.5, snapshot.totalProfitLossAmount)
        with(snapshot.positions.single()) {
            assertEquals("TQQQ", symbol)
            assertEquals("ProShares UltraPro QQQ", stockName)
            assertEquals(3, quantity)
            assertEquals(112.5, averagePurchasePrice)
            assertEquals(120.0, currentPrice)
            assertEquals(337.5, purchaseAmount)
            assertEquals(360.0, evaluationAmount)
            assertEquals(22.5, profitLossAmount)
        }
        assertEquals("/open-api/overseas/trading/inquire-balance", exchangeFunction.requests.single().url().path)
        assertEquals("NASD", exchangeFunction.requests.single().queryValue("ovrsExcgCd"))
        assertEquals("USD", exchangeFunction.requests.single().queryValue("trCrcyCd"))
    }

    @Test
    fun `domestic account snapshot maps balance positions and summary`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "rt_cd": "0",
                  "ctx_area_fk100": "",
                  "ctx_area_nk100": "",
                  "output1": [
                    {
                      "pdno": "005930",
                      "prdt_name": "Samsung Electronics",
                      "hldg_qty": "3",
                      "pchs_avg_pric": "70000",
                      "prpr": "72000",
                      "pchs_amt": "210000",
                      "evlu_amt": "216000",
                      "evlu_pfls_amt": "6000"
                    }
                  ],
                  "output2": {
                    "ord_psbl_cash": "1200000",
                    "dnca_tot_amt": "1250000",
                    "nxdy_excc_amt": "1100000",
                    "pchs_amt_smtl": "210000",
                    "tot_evlu_amt": "216000",
                    "evlu_pfls_smtl": "6000"
                  }
                }
                """.trimIndent(),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val snapshot = adapter.findAccountSnapshot(
            BrokerAccountSnapshotQuery(
                market = StockOrderMarket.DOMESTIC,
                isMock = true,
            ),
        )

        assertEquals(StockOrderMarket.DOMESTIC, snapshot.market)
        assertEquals("KRX", snapshot.exchange)
        assertEquals("KRW", snapshot.currency)
        assertEquals(1_200_000.0, snapshot.availableCashAmount)
        assertEquals("KRW", snapshot.cashCurrency)
        assertEquals(1_200_000.0, snapshot.orderableCashAmount)
        assertEquals(1_250_000.0, snapshot.settledCashAmount)
        assertEquals(1_100_000.0, snapshot.withdrawableCashAmount)
        assertEquals(210_000.0, snapshot.totalPurchaseAmount)
        assertEquals(216_000.0, snapshot.totalEvaluationAmount)
        assertEquals(6_000.0, snapshot.totalProfitLossAmount)
        with(snapshot.positions.single()) {
            assertEquals("005930", symbol)
            assertEquals("Samsung Electronics", stockName)
            assertEquals(3, quantity)
            assertEquals(70_000.0, averagePurchasePrice)
            assertEquals(72_000.0, currentPrice)
            assertEquals(210_000.0, purchaseAmount)
            assertEquals(216_000.0, evaluationAmount)
            assertEquals(6_000.0, profitLossAmount)
        }
        assertEquals("/open-api/trading/inquire-balance", exchangeFunction.requests.single().url().path)
        assertEquals("true", exchangeFunction.requests.single().queryValue("isMock"))
        assertEquals("02", exchangeFunction.requests.single().queryValue("inqrDvsn"))
        assertEquals("01", exchangeFunction.requests.single().queryValue("unprDvsn"))
    }

    @Test
    fun `domestic account snapshot maps output2 array summary`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "rt_cd": "0",
                  "ctx_area_fk100": "",
                  "ctx_area_nk100": "",
                  "output1": [],
                  "output2": [
                    {
                      "ORD_PSBL_CASH": "1200000",
                      "DNCA_TOT_AMT": "1250000",
                      "WDRW_PSBL_AMT": "1100000",
                      "TOT_PCHS_AMT": "210000",
                      "SCTS_EVLU_AMT": "216000",
                      "TOT_EVLU_PFLS_AMT": "6000"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val snapshot = adapter.findAccountSnapshot(
            BrokerAccountSnapshotQuery(
                market = StockOrderMarket.DOMESTIC,
                isMock = true,
            ),
        )

        assertEquals(1_200_000.0, snapshot.availableCashAmount)
        assertEquals(1_200_000.0, snapshot.orderableCashAmount)
        assertEquals(1_250_000.0, snapshot.settledCashAmount)
        assertEquals(1_100_000.0, snapshot.withdrawableCashAmount)
        assertEquals(210_000.0, snapshot.totalPurchaseAmount)
        assertEquals(216_000.0, snapshot.totalEvaluationAmount)
        assertEquals(6_000.0, snapshot.totalProfitLossAmount)
    }

    @Test
    fun `overseas account snapshot maps uppercase top level balance aliases`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "RT_CD": "0",
                  "CTX_AREA_FK200": "",
                  "CTX_AREA_NK200": "",
                  "OUTPUT1": {
                    "OVRS_PDNO": "TQQQ",
                    "OVRS_ITEM_NAME": "ProShares UltraPro QQQ",
                    "OVRS_CBLC_QTY": "3",
                    "PCHS_AVG_PRIC": "112.5",
                    "NOW_PRIC2": "120.0",
                    "FRCR_PCHS_AMT1": "337.5",
                    "OVRS_STCK_EVLU_AMT": "360.0",
                    "FRCR_EVLU_PFLS_AMT": "22.5"
                  },
                  "OUTPUT2": {
                    "OVRS_ORD_PSBL_AMT": "1250.25",
                    "FRCR_DNCL_AMT_2": "1300.00",
                    "FRCR_WDRW_PSBL_AMT": "1200.50",
                    "FRCR_BUY_AMT_SMTL": "337.5",
                    "TOT_EVLU_AMT": "360.0",
                    "TOT_EVLU_PFLS_AMT": "22.5"
                  }
                }
                """.trimIndent(),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val snapshot = adapter.findAccountSnapshot(
            BrokerAccountSnapshotQuery(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                isMock = false,
            ),
        )

        assertEquals(1250.25, snapshot.availableCashAmount)
        assertEquals("USD", snapshot.cashCurrency)
        assertEquals(1250.25, snapshot.orderableCashAmount)
        assertEquals(1300.0, snapshot.settledCashAmount)
        assertEquals(1200.5, snapshot.withdrawableCashAmount)
        assertEquals(337.5, snapshot.totalPurchaseAmount)
        assertEquals(360.0, snapshot.totalEvaluationAmount)
        assertEquals(22.5, snapshot.totalProfitLossAmount)
        with(snapshot.positions.single()) {
            assertEquals("TQQQ", symbol)
            assertEquals("ProShares UltraPro QQQ", stockName)
            assertEquals(3, quantity)
            assertEquals(112.5, averagePurchasePrice)
            assertEquals(120.0, currentPrice)
            assertEquals(337.5, purchaseAmount)
            assertEquals(360.0, evaluationAmount)
            assertEquals(22.5, profitLossAmount)
        }
    }

    @Test
    fun `overseas account snapshot maps output2 array summary`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "rt_cd": "0",
                  "ctx_area_fk200": "",
                  "ctx_area_nk200": "",
                  "output1": [],
                  "output2": [
                    {
                      "ord_psbl_frcr_amt": "1250.25",
                      "frcr_dncl_amt": "1300.00",
                      "ovrs_wdrw_psbl_amt": "1200.50",
                      "pchs_amt_smtl": "337.5",
                      "frcr_evlu_amt2": "360.0",
                      "evlu_pfls_amt_smtl": "22.5"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val snapshot = adapter.findAccountSnapshot(
            BrokerAccountSnapshotQuery(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                isMock = false,
            ),
        )

        assertEquals(1250.25, snapshot.availableCashAmount)
        assertEquals(1250.25, snapshot.orderableCashAmount)
        assertEquals(1300.0, snapshot.settledCashAmount)
        assertEquals(1200.5, snapshot.withdrawableCashAmount)
        assertEquals(337.5, snapshot.totalPurchaseAmount)
        assertEquals(360.0, snapshot.totalEvaluationAmount)
        assertEquals(22.5, snapshot.totalProfitLossAmount)
    }

    @Test
    fun `overseas account snapshot follows balance pagination cursor`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "rt_cd": "0",
                  "ctx_area_fk200": "FK1",
                  "ctx_area_nk200": "NK1",
                  "output1": [
                    {
                      "ovrs_pdno": "TQQQ",
                      "ovrs_item_name": "ProShares UltraPro QQQ",
                      "ovrs_cblc_qty": "3"
                    }
                  ],
                  "output2": {
                    "ovrs_ord_psbl_amt": "1250.25",
                    "tot_evlu_amt": "360.0"
                  }
                }
                """.trimIndent(),
                """
                {
                  "rt_cd": "0",
                  "ctx_area_fk200": "",
                  "ctx_area_nk200": "",
                  "output1": [
                    {
                      "ovrs_pdno": "SOXL",
                      "ovrs_item_name": "Direxion Daily Semiconductor Bull 3X Shares",
                      "ovrs_cblc_qty": "2"
                    }
                  ],
                  "output2": {}
                }
                """.trimIndent(),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val snapshot = adapter.findAccountSnapshot(
            BrokerAccountSnapshotQuery(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                isMock = false,
            ),
        )

        assertEquals(listOf("TQQQ", "SOXL"), snapshot.positions.map { it.symbol })
        assertEquals(1250.25, snapshot.availableCashAmount)
        assertEquals("USD", snapshot.cashCurrency)
        assertEquals(1250.25, snapshot.orderableCashAmount)
        assertEquals(360.0, snapshot.totalEvaluationAmount)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("FK1", exchangeFunction.requests[1].queryValue("ctxAreaFk200"))
        assertEquals("NK1", exchangeFunction.requests[1].queryValue("ctxAreaNk200"))
    }

    @Test
    fun `overseas account snapshot maps kis rate limit to temporary unavailable`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "rtCode": "1",
                  "msgCode": "EGW00201",
                  "message": "per-second transaction limit exceeded"
                }
                """.trimIndent(),
            ),
        )
        val properties = KisBrokerGatewayProperties().apply {
            queryMaxAttempts = 1
        }
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
            properties = properties,
        )

        val exception = assertFailsWith<BrokerOrderTemporaryUnavailableException> {
            adapter.findAccountSnapshot(
                BrokerAccountSnapshotQuery(
                    market = StockOrderMarket.OVERSEAS_US,
                    exchange = "NASD",
                    currency = "USD",
                    isMock = false,
                ),
            )
        }

        assertEquals("1", exception.brokerReturnCode)
        assertEquals("EGW00201", exception.brokerMessageCode)
        assertEquals("per-second transaction limit exceeded", exception.brokerMessage)
        assertEquals(
            "overseas balance lookup failed: EGW00201 per-second transaction limit exceeded",
            exception.message,
        )
    }

    @Test
    fun `overseas account snapshot retries kis temporary business failure`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "rtCode": "1",
                  "msgCode": "EGW00201",
                  "message": "per-second transaction limit exceeded"
                }
                """.trimIndent(),
                """
                {
                  "rt_cd": "0",
                  "ctx_area_fk200": "",
                  "ctx_area_nk200": "",
                  "output1": [
                    {
                      "ovrs_pdno": "TQQQ",
                      "ovrs_item_name": "ProShares UltraPro QQQ",
                      "ovrs_cblc_qty": "3"
                    }
                  ],
                  "output2": {
                    "ovrs_ord_psbl_amt": "1250.25"
                  }
                }
                """.trimIndent(),
            ),
        )
        val properties = KisBrokerGatewayProperties().apply {
            queryMaxAttempts = 2
            queryBackoff = java.time.Duration.ZERO
            rateLimit.enabled = false
            circuitBreaker.failureThreshold = 5
        }
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
            properties = properties,
        )

        val snapshot = adapter.findAccountSnapshot(
            BrokerAccountSnapshotQuery(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                isMock = false,
            ),
        )

        assertEquals("TQQQ", snapshot.positions.single().symbol)
        assertEquals(1250.25, snapshot.availableCashAmount)
        assertEquals(1250.25, snapshot.orderableCashAmount)
        assertEquals(2, exchangeFunction.requests.size)
    }

    @Test
    fun `overseas order history maps kis business error to query failed`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf(
                """
                {
                  "RT_CODE": "1",
                  "MSG_CODE": "APBK9999",
                  "MSG": "invalid account scope"
                }
                """.trimIndent(),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderQueryFailedException> {
            adapter.findOrderHistory(historyQuery())
        }

        assertEquals("1", exception.brokerReturnCode)
        assertEquals("APBK9999", exception.brokerMessageCode)
        assertEquals("invalid account scope", exception.brokerMessage)
        assertEquals("overseas execution lookup failed: APBK9999 invalid account scope", exception.message)
    }

    @Test
    fun `domestic order history maps kis business error to query failed`() {
        val adapter = domesticHistoryAdapter(
            DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersResponse.newBuilder()
                .setRtCd("1")
                .setMsgCd("APBK0001")
                .setMsg1("domestic query rejected")
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderQueryFailedException> {
            adapter.findOrderHistory(domesticHistoryQuery())
        }

        assertEquals("1", exception.brokerReturnCode)
        assertEquals("APBK0001", exception.brokerMessageCode)
        assertEquals("domestic query rejected", exception.brokerMessage)
        assertEquals("domestic execution lookup failed: APBK0001 domestic query rejected", exception.message)
    }

    @Test
    fun `domestic order history sends broker order id when present`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(protobufResponse(domesticHistoryResponse())),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        adapter.findOrderHistory(
            BrokerOrderHistoryQuery(
                market = StockOrderMarket.DOMESTIC,
                symbol = "",
                externalOrderId = "domestic-order-1",
                branchOrderNumber = "00001",
                from = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
                to = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
                isMock = false,
            ),
        )

        assertEquals("domestic-order-1", exchangeFunction.requests.single().queryValue("odno"))
        assertEquals("00001", exchangeFunction.requests.single().queryValue("ordGnoBrno"))
        assertEquals("", exchangeFunction.requests.single().queryValue("pdno"))
        assertEquals("20260602", exchangeFunction.requests.single().queryValue("inqrStrtDt"))
        assertEquals("20260602", exchangeFunction.requests.single().queryValue("inqrEndDt"))
    }

    @Test
    fun `maps domestic order org number as branch order number fallback`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1.newBuilder()
                    .setOrdDt("20260602")
                    .setOrdOrgno("00007")
                    .setOdno("domestic-order-orgno")
                    .setSllBuyDvsnCd("02")
                    .setPdno("005930")
                    .setPrdtName("Samsung Electronics")
                    .setOrdQty("3")
                    .setOrdTmd("093000")
                    .setTotCcldQty("0")
                    .setRmnQty("3")
                    .build(),
            ),
        )

        val item = adapter.findOrderHistory(domesticHistoryQuery()).single()

        assertEquals("00007", item.branchOrderNumber)
        assertEquals(BrokerOrderStatus.SUBMITTED, item.toStatus().status)
    }

    @Test
    fun `uses domestic order org number when branch order number is all zero placeholder`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1.newBuilder()
                    .setOrdDt("20260602")
                    .setOrdGnoBrno("0000000000")
                    .setOrdOrgno("00007")
                    .setOdno("domestic-zero-branch")
                    .setSllBuyDvsnCd("02")
                    .setPdno("005930")
                    .setPrdtName("Samsung Electronics")
                    .setOrdQty("3")
                    .setOrdTmd("093000")
                    .setTotCcldQty("0")
                    .setRmnQty("3")
                    .build(),
            ),
        )

        val item = adapter.findOrderHistory(domesticHistoryQuery()).single()

        assertEquals("00007", item.branchOrderNumber)
        assertEquals(BrokerOrderStatus.SUBMITTED, item.toStatus().status)
    }

    @Test
    fun `ignores domestic all zero branch order number placeholder`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1.newBuilder()
                    .setOrdDt("20260602")
                    .setOrdGnoBrno("0000000000")
                    .setOdno("domestic-zero-branch")
                    .setSllBuyDvsnCd("02")
                    .setPdno("005930")
                    .setPrdtName("Samsung Electronics")
                    .setOrdQty("3")
                    .setOrdTmd("093000")
                    .setTotCcldQty("0")
                    .setRmnQty("3")
                    .build(),
            ),
        )

        val item = adapter.findOrderHistory(domesticHistoryQuery()).single()

        assertEquals(null, item.branchOrderNumber)
        assertEquals(BrokerOrderStatus.SUBMITTED, item.toStatus().status)
    }

    @Test
    fun `ignores domestic all zero original order id placeholder`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1.newBuilder()
                    .setOrdDt("20260602")
                    .setOrdGnoBrno("00001")
                    .setOdno("domestic-order-zero-original")
                    .setOrgnOdno("0000000000")
                    .setSllBuyDvsnCd("02")
                    .setPdno("005930")
                    .setPrdtName("Samsung Electronics")
                    .setOrdQty("3")
                    .setOrdTmd("093000")
                    .setTotCcldQty("0")
                    .setRmnQty("3")
                    .build(),
            ),
        )

        val item = adapter.findOrderHistory(domesticHistoryQuery()).single()

        assertEquals(null, item.originalOrderId)
        assertEquals(BrokerOrderStatus.SUBMITTED, item.toStatus().status)
    }

    @Test
    fun `ignores domestic all zero order id placeholder`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1.newBuilder()
                    .setOrdDt("20260602")
                    .setOrdGnoBrno("00001")
                    .setOdno("0000000000")
                    .setSllBuyDvsnCd("02")
                    .setPdno("005930")
                    .setPrdtName("Samsung Electronics")
                    .setOrdQty("3")
                    .setOrdTmd("093000")
                    .setTotCcldQty("0")
                    .setRmnQty("3")
                    .build(),
            ),
        )

        assertEquals(emptyList(), adapter.findOrderHistory(domesticHistoryQuery()))
    }

    @Test
    fun `maps domestic rejected quantity to rejected status`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                domesticRow(
                    orderId = "rejected-domestic-order",
                    orderedQuantity = "3",
                    filledQuantity = "0",
                    remainingQuantity = "0",
                    rejectedQuantity = "3",
                ),
            ),
        )

        val status = adapter.findOrderHistory(domesticHistoryQuery()).single().toStatus()

        assertEquals(BrokerOrderStatus.REJECTED, status.status)
        assertEquals("broker rejected quantity=3", status.reason)
    }

    @Test
    fun `maps domestic status and rejection reason fields to rejected status`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                domesticRow(
                    orderId = "status-rejected-domestic-order",
                    orderedQuantity = "3",
                    filledQuantity = "0",
                    remainingQuantity = "0",
                    statusName = "Rejected",
                    rejectionReason = "insufficient quantity",
                ),
            ),
        )

        val status = adapter.findOrderHistory(domesticHistoryQuery()).single().toStatus()

        assertEquals(BrokerOrderStatus.REJECTED, status.status)
        assertEquals("insufficient quantity", status.reason)
    }

    @Test
    fun `maps domestic rejection reason code to rejected status`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                domesticRow(
                    orderId = "code-rejected-domestic-order",
                    orderedQuantity = "3",
                    filledQuantity = "0",
                    remainingQuantity = "0",
                    statusName = "Accepted",
                    rejectionReasonCode = "APBK001",
                ),
            ),
        )

        val status = adapter.findOrderHistory(domesticHistoryQuery()).single().toStatus()

        assertEquals(BrokerOrderStatus.REJECTED, status.status)
        assertEquals("APBK001", status.reason)
    }

    @Test
    fun `ignores domestic non rejection marker paired with reason code`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                domesticRow(
                    orderId = "not-rejected-domestic-order",
                    orderedQuantity = "10",
                    filledQuantity = "4",
                    remainingQuantity = "6",
                    statusName = "Accepted",
                    rejectionReason = "Not rejected",
                    rejectionReasonCode = "APBK001",
                ),
            ),
        )

        val item = adapter.findOrderHistory(domesticHistoryQuery()).single()
        val status = item.toStatus()

        assertEquals(null, item.rejectionReason)
        assertEquals(0L, item.rejectedQuantity)
        assertEquals(BrokerOrderStatus.PARTIALLY_FILLED, status.status)
        assertEquals("broker partially filled quantity=4 remaining=6", status.reason)
    }

    @Test
    fun `maps domestic cancel fields to cancelled status`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                domesticRow(
                    orderId = "cancelled-domestic-order",
                    orderedQuantity = "3",
                    filledQuantity = "0",
                    remainingQuantity = "3",
                    cancelledQuantity = "3",
                    cancelled = true,
                ),
            ),
        )

        val status = adapter.findOrderHistory(domesticHistoryQuery()).single().toStatus()

        assertEquals(BrokerOrderStatus.CANCELLED, status.status)
        assertEquals("broker cancelled quantity=3", status.reason)
    }

    @Test
    fun `maps domestic partial fill as cumulative execution and partially filled status`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                domesticRow(
                    orderId = "partial-domestic-order",
                    executionId = "domestic-fill-1",
                    orderedQuantity = "3",
                    filledQuantity = "1",
                    remainingQuantity = "2",
                    orderedPrice = "71000",
                    averagePrice = "71200.5",
                ),
            ),
        )

        val item = adapter.findOrderHistory(domesticHistoryQuery()).single()
        val execution = item.toExecutionDto()
        val status = item.toStatus()

        assertEquals(BrokerOrderStatus.PARTIALLY_FILLED, status.status)
        assertEquals(3L, status.orderedQuantity)
        assertEquals(71000.0, status.orderedPrice)
        assertEquals(1L, status.cumulativeFilledQuantity)
        assertEquals(2L, status.remainingQuantity)
        checkNotNull(execution)
        assertEquals("005930", execution.stockId)
        assertEquals("partial-domestic-order", execution.externalOrderId)
        assertEquals("domestic-fill-1", execution.externalExecutionId)
        assertEquals(1, execution.quantity)
        assertEquals(ExecutionTypeDto.PURCHASE, execution.type)
        assertEquals(71200.5, execution.averageExecutionPrice)
        assertEquals(ExecutionQuantityModeDto.CUMULATIVE, execution.quantityMode)
    }

    @Test
    fun `ignores domestic all zero execution id placeholder`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                domesticRow(
                    orderId = "domestic-zero-execution-id-order",
                    executionId = "0000000000",
                    orderedQuantity = "3",
                    filledQuantity = "1",
                    remainingQuantity = "2",
                    averagePrice = "71200.5",
                ),
            ),
        )

        val item = adapter.findOrderHistory(domesticHistoryQuery()).single()
        val execution = item.toExecutionDto()
        val status = item.toStatus()

        assertEquals(BrokerOrderStatus.PARTIALLY_FILLED, status.status)
        assertEquals("domestic-zero-execution-id-order:1:PURCHASE", status.externalExecutionId)
        checkNotNull(execution)
        assertEquals("domestic-zero-execution-id-order:1:PURCHASE", execution.externalExecutionId)
    }

    @Test
    fun `maps domestic full fill as cumulative execution and filled status`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                domesticRow(
                    orderId = "filled-domestic-order",
                    orderedQuantity = "3",
                    filledQuantity = "3",
                    remainingQuantity = "0",
                    averagePrice = "71300",
                ),
            ),
        )

        val item = adapter.findOrderHistory(domesticHistoryQuery()).single()
        val execution = item.toExecutionDto()
        val status = item.toStatus()

        assertEquals(BrokerOrderStatus.FILLED, status.status)
        assertEquals(3L, status.orderedQuantity)
        assertEquals(3L, status.cumulativeFilledQuantity)
        assertEquals(0L, status.remainingQuantity)
        checkNotNull(execution)
        assertEquals("filled-domestic-order", execution.externalOrderId)
        assertEquals(3, execution.quantity)
        assertEquals(71300.0, execution.averageExecutionPrice)
        assertEquals(ExecutionQuantityModeDto.CUMULATIVE, execution.quantityMode)
    }

    @Test
    fun `maps domestic side name fallback as selling execution`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1.newBuilder()
                    .setOrdDt("20260602")
                    .setOrdGnoBrno("00001")
                    .setOdno("side-name-domestic-sell")
                    .setSllBuyDvsnCd("")
                    .setSllBuyDvsnCdName("SELL")
                    .setPdno("005930")
                    .setPrdtName("Samsung Electronics")
                    .setOrdQty("3")
                    .setOrdTmd("093000")
                    .setTotCcldQty("3")
                    .setAvgPrvs("71300")
                    .setRmnQty("0")
                    .build(),
            ),
        )

        val execution = adapter.findOrderHistory(domesticHistoryQuery()).single().toExecutionDto()

        checkNotNull(execution)
        assertEquals(ExecutionTypeDto.SELLING, execution.type)
        assertEquals("side-name-domestic-sell", execution.externalOrderId)
    }

    @Test
    fun `overseas order status lookup leaves unsupported broker order id query blank`() {
        val query = BrokerOrderHistoryQuery(
            market = StockOrderMarket.OVERSEAS_US,
            symbol = "TQQQ",
            externalOrderId = "broker-order-1",
            branchOrderNumber = "00009",
            from = ZonedDateTime.parse("2026-06-01T09:00:00+09:00"),
            to = ZonedDateTime.parse("2026-06-01T09:00:00+09:00"),
            isMock = false,
        )

        val params = query.toKisOverseasExecutionOrderQuery()

        assertEquals("", params["odno"])
        assertEquals("00009", params["ordGnoBrno"])
        assertEquals("TQQQ", params["pdno"])
    }

    @Test
    fun `maps overseas rejection reason name to rejected status`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "odno": "rejected-order",
                  "pdno": "TQQQ",
                  "prdt_name": "ProShares UltraPro QQQ",
                  "ord_dt": "20260602",
                  "ord_tmd": "093000",
                  "ft_ord_qty": "3",
                  "ft_ccld_qty": "0",
                  "nccs_qty": "0",
                  "sll_buy_dvsn_cd": "02",
                  "prcs_stat_name": "접수거부",
                  "rjct_rson": "",
                  "rjct_rson_name": "주문가능수량 부족"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val status = item.toStatus()

        assertEquals(BrokerOrderStatus.REJECTED, status.status)
        assertEquals("주문가능수량 부족", status.reason)
    }

    @Test
    fun `maps overseas rejection reason code to rejected status`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "rejected-code-order",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "ORD_QTY": "3",
                  "TOT_CCLD_QTY": "0",
                  "RMN_QTY": "0",
                  "SLL_BUY_DVSN_NAME": "BUY",
                  "PRCS_STAT_NAME": "Accepted",
                  "RJCT_RSON_CD": "APBK001"
                }
              ]
            }
            """.trimIndent(),
        )

        val status = adapter.findOrderHistory(historyQuery()).single().toStatus()

        assertEquals(BrokerOrderStatus.REJECTED, status.status)
        assertEquals("APBK001", status.reason)
    }

    @Test
    fun `ignores overseas non rejection marker paired with reason code`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "not-rejected-overseas-order",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "ORD_QTY": "10",
                  "TOT_CCLD_QTY": "4",
                  "RMN_QTY": "6",
                  "SLL_BUY_DVSN_NAME": "BUY",
                  "PRCS_STAT_NAME": "Accepted",
                  "RJCT_RSON_CD": "APBK001",
                  "RJCT_RSON_CD_NAME": "not rejected"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val status = item.toStatus()

        assertEquals(null, item.rejectionReason)
        assertEquals(0L, item.rejectedQuantity)
        assertEquals(BrokerOrderStatus.PARTIALLY_FILLED, status.status)
        assertEquals("broker partially filled quantity=4 remaining=6", status.reason)
    }

    @Test
    fun `maps overseas cancel status from revision cancel field`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "odno": "cancelled-order",
                  "pdno": "TQQQ",
                  "prdt_name": "ProShares UltraPro QQQ",
                  "ord_dt": "20260602",
                  "ord_tmd": "093000",
                  "ft_ord_qty": "3",
                  "ft_ccld_qty": "0",
                  "nccs_qty": "3",
                  "sll_buy_dvsn_cd": "02",
                  "prcs_stat_name": "처리완료",
                  "rvse_cncl_dvsn_name": "취소"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val status = item.toStatus()

        assertEquals(BrokerOrderStatus.CANCELLED, status.status)
        assertEquals("처리완료; 취소", status.reason)
    }

    @Test
    fun `maps overseas partial fill as cumulative execution and partially filled status`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "odno": "partial-order",
                  "pdno": "TQQQ",
                  "prdt_name": "ProShares UltraPro QQQ",
                  "ord_dt": "20260602",
                  "ord_tmd": "093000",
                  "ft_ord_qty": "3",
                  "ft_ord_unpr": "112.5",
                  "ccld_no": "overseas-fill-1",
                  "ft_ccld_qty": "1",
                  "nccs_qty": "2",
                  "sll_buy_dvsn_cd": "02",
                  "ft_ccld_unpr3": "112.5",
                  "prcs_stat_name": "접수"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val execution = item.toExecutionDto()
        val status = item.toStatus()

        assertEquals(BrokerOrderStatus.PARTIALLY_FILLED, status.status)
        assertEquals(3L, status.orderedQuantity)
        assertEquals(112.5, status.orderedPrice)
        assertEquals(1L, status.cumulativeFilledQuantity)
        assertEquals(2L, status.remainingQuantity)
        checkNotNull(execution)
        assertEquals("partial-order", execution.externalOrderId)
        assertEquals("overseas-fill-1", execution.externalExecutionId)
        assertEquals(1, execution.quantity)
        assertEquals(112.5, execution.averageExecutionPrice)
        assertEquals(ExecutionQuantityModeDto.CUMULATIVE, execution.quantityMode)
    }

    @Test
    fun `ignores overseas all zero execution id placeholder`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "overseas-zero-execution-id-order",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "ORD_QTY": "3",
                  "CCLD_NO": "0000000000",
                  "TOT_CCLD_QTY": "1",
                  "RMN_QTY": "2",
                  "SLL_BUY_DVSN_NAME": "BUY",
                  "AVG_PRVS": "112.5"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val execution = item.toExecutionDto()
        val status = item.toStatus()

        assertEquals(BrokerOrderStatus.PARTIALLY_FILLED, status.status)
        assertEquals("overseas-zero-execution-id-order:1:PURCHASE", status.externalExecutionId)
        checkNotNull(execution)
        assertEquals("overseas-zero-execution-id-order:1:PURCHASE", execution.externalExecutionId)
    }

    @Test
    fun `maps overseas alias fields as cumulative execution`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "alias-order",
                  "ORD_ORGNO": "00002",
                  "PDNO": "TQQQ",
                  "PRDT_ENG_NAME": "ProShares UltraPro QQQ",
                  "ORD_DT": "20260602",
                  "THCO_ORD_TMD": "093500",
                  "ORD_QTY": "4",
                  "ORD_UNPR": "113.75",
                  "CCLD_QTY": "2",
                  "RMN_QTY": "2",
                  "SLL_BUY_DVSN_NAME": "BUY",
                  "FT_CCLD_UNPR": "113.75"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val execution = item.toExecutionDto()

        assertEquals("alias-order", item.externalOrderId)
        assertEquals("00002", item.branchOrderNumber)
        assertEquals(4, item.orderedQuantity)
        assertEquals(113.75, item.orderedPrice)
        assertEquals(2, item.cumulativeFilledQuantity)
        assertEquals(2, item.remainingQuantity)
        assertEquals(OrderIntentSide.BUY, item.side)
        checkNotNull(execution)
        assertEquals("TQQQ", execution.stockId)
        assertEquals("ProShares UltraPro QQQ", execution.stockName)
        assertEquals(2, execution.quantity)
        assertEquals(113.75, execution.averageExecutionPrice)
        assertEquals(ExecutionTypeDto.PURCHASE, execution.type)
    }

    @Test
    fun `maps overseas prefixed quantity aliases as cumulative execution`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "prefixed-quantity-order",
                  "PDNO": "TQQQ",
                  "PRDT_ENG_NAME": "ProShares UltraPro QQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "OVRS_ORD_QTY": "5",
                  "OVRS_CCLD_QTY": "2",
                  "OVRS_NCCS_QTY": "3",
                  "OVRS_CCLD_UNPR": "113.75",
                  "SLL_BUY_DVSN_NAME": "BUY"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val execution = item.toExecutionDto()
        val status = item.toStatus()

        assertEquals(BrokerOrderStatus.PARTIALLY_FILLED, status.status)
        assertEquals(5L, item.orderedQuantity)
        assertEquals(2L, item.cumulativeFilledQuantity)
        assertEquals(3L, item.remainingQuantity)
        assertEquals(113.75, item.averageExecutionPrice)
        checkNotNull(execution)
        assertEquals("prefixed-quantity-order", execution.externalOrderId)
        assertEquals(2, execution.quantity)
        assertEquals(113.75, execution.averageExecutionPrice)
    }

    @Test
    fun `ignores overseas all zero original order id placeholder`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "overseas-order-zero-original",
                  "ORGN_ODNO": "0000000000",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "ORD_QTY": "3",
                  "TOT_CCLD_QTY": "0",
                  "RMN_QTY": "3",
                  "SLL_BUY_DVSN_NAME": "BUY"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()

        assertEquals(null, item.originalOrderId)
        assertEquals(BrokerOrderStatus.SUBMITTED, item.toStatus().status)
    }

    @Test
    fun `ignores overseas all zero order id placeholder`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "0000000000",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "ORD_QTY": "3",
                  "TOT_CCLD_QTY": "0",
                  "RMN_QTY": "3",
                  "SLL_BUY_DVSN_NAME": "BUY"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), adapter.findOrderHistory(historyQuery()))
    }

    @Test
    fun `maps overseas camel case history aliases as cumulative execution`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rtCd": "0",
              "ctxAreaFk200": "",
              "ctxAreaNk200": "",
              "output": [
                {
                  "ordNo": "camel-order",
                  "orgnOdno": "original-camel-order",
                  "ordGnoBrno": "00003",
                  "ovrsPdno": "TQQQ",
                  "prdtEngName": "ProShares UltraPro QQQ",
                  "ordDt": "20260602",
                  "thcoOrdTmd": "093500",
                  "ftOrdQty": "4",
                  "ftOrdUnpr3": "113.75",
                  "ftCcldNo": "camel-fill-1",
                  "totCcldQty": "2",
                  "rmnQty": "2",
                  "sllBuyDvsnCdName": "BUY",
                  "avgPrvs": "113.75"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val status = item.toStatus()
        val execution = item.toExecutionDto()

        assertEquals("camel-order", item.externalOrderId)
        assertEquals("original-camel-order", item.originalOrderId)
        assertEquals("00003", item.branchOrderNumber)
        assertEquals(4, item.orderedQuantity)
        assertEquals(113.75, item.orderedPrice)
        assertEquals(2, item.cumulativeFilledQuantity)
        assertEquals(2, item.remainingQuantity)
        assertEquals(OrderIntentSide.BUY, item.side)
        assertEquals(BrokerOrderStatus.PARTIALLY_FILLED, status.status)
        checkNotNull(execution)
        assertEquals("camel-fill-1", execution.externalExecutionId)
        assertEquals(2, execution.quantity)
        assertEquals(113.75, execution.averageExecutionPrice)
    }

    @Test
    fun `maps overseas camel case rejection and cancellation aliases`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rtCd": "0",
              "ctxAreaFk200": "",
              "ctxAreaNk200": "",
              "output": [
                {
                  "ordNo": "camel-rejected-order",
                  "pdno": "TQQQ",
                  "ordDt": "20260602",
                  "ordTmd": "093000",
                  "ordQty": "3",
                  "totCcldQty": "0",
                  "rmnQty": "0",
                  "sllBuyDvsnCdName": "BUY",
                  "rjctRsonCd": "APBK001"
                },
                {
                  "ordNo": "camel-cancelled-order",
                  "pdno": "TQQQ",
                  "ordDt": "20260602",
                  "ordTmd": "093100",
                  "ordQty": "3",
                  "totCcldQty": "0",
                  "rmnQty": "3",
                  "cnclYn": "Y",
                  "cnclCfrmQty": "3",
                  "sllBuyDvsnCdName": "BUY"
                }
              ]
            }
            """.trimIndent(),
        )

        val statuses = adapter.findOrderHistory(historyQuery()).map { it.toStatus() }

        assertEquals(BrokerOrderStatus.REJECTED, statuses[0].status)
        assertEquals("APBK001", statuses[0].reason)
        assertEquals(BrokerOrderStatus.CANCELLED, statuses[1].status)
        assertEquals("broker cancelled quantity=3", statuses[1].reason)
    }

    @Test
    fun `maps overseas official order price alias for status disambiguation`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "alias-price-order",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "FT_ORD_QTY": "3",
                  "FT_ORD_UNPR3": "112.5",
                  "TOT_CCLD_QTY": "0",
                  "RMN_QTY": "3",
                  "SLL_BUY_DVSN_NAME": "BUY"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val status = listOf(item).findStatusFor(
            BrokerOrderStatusQuery(
                orderIntentId = UUID.randomUUID(),
                internalOrderId = UUID.randomUUID(),
                externalOrderId = null,
                symbol = "TQQQ",
                side = OrderIntentSide.BUY,
                orderedQuantity = 3,
                submittedPrice = 112.5,
                market = StockOrderMarket.OVERSEAS_US,
                submittedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals(112.5, item.orderedPrice)
        assertEquals(BrokerOrderStatus.SUBMITTED, status.status)
        assertEquals("alias-price-order", status.externalOrderId)
        assertEquals(112.5, status.orderedPrice)
    }

    @Test
    fun `maps overseas english rejected status`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "english-rejected-order",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "ORD_QTY": "3",
                  "TOT_CCLD_QTY": "0",
                  "RMN_QTY": "0",
                  "SLL_BUY_DVSN_NAME": "BUY",
                  "PRCS_STAT_NAME": "Rejected"
                }
              ]
            }
            """.trimIndent(),
        )

        val status = adapter.findOrderHistory(historyQuery()).single().toStatus()

        assertEquals(BrokerOrderStatus.REJECTED, status.status)
        assertEquals("Rejected", status.reason)
    }

    @Test
    fun `maps overseas explicit rejected quantity as rejected status`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "quantity-rejected-order",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "ORD_QTY": "3",
                  "RJCT_QTY": "3",
                  "TOT_CCLD_QTY": "0",
                  "RMN_QTY": "0",
                  "SLL_BUY_DVSN_NAME": "BUY"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val status = item.toStatus()

        assertEquals(3, item.rejectedQuantity)
        assertEquals(BrokerOrderStatus.REJECTED, status.status)
        assertEquals("broker rejected quantity=3", status.reason)
    }

    @Test
    fun `maps overseas rejected flag as rejected status`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "flag-rejected-order",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "ORD_QTY": "3",
                  "RJCT_YN": "Y",
                  "TOT_CCLD_QTY": "0",
                  "RMN_QTY": "0",
                  "SLL_BUY_DVSN_NAME": "BUY"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val status = item.toStatus()

        assertEquals(3, item.rejectedQuantity)
        assertEquals("broker rejected flag=Y", item.rejectionReason)
        assertEquals(BrokerOrderStatus.REJECTED, status.status)
        assertEquals("broker rejected flag=Y", status.reason)
    }

    @Test
    fun `maps overseas english cancelled status and explicit cancel quantity`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ODNO": "english-cancelled-order",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "ORD_QTY": "3",
                  "TOT_CCLD_QTY": "0",
                  "RMN_QTY": "3",
                  "CNCL_QTY": "3",
                  "SLL_BUY_DVSN_NAME": "BUY",
                  "PRCS_STAT_NAME": "Cancelled"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = adapter.findOrderHistory(historyQuery()).single()
        val status = item.toStatus()

        assertEquals(3, item.cancelledQuantity)
        assertEquals(BrokerOrderStatus.CANCELLED, status.status)
        assertEquals("Cancelled", status.reason)
    }

    @Test
    fun `maps overseas cancel revision code and original broker order id`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ORD_NO": "cancel-order-1",
                  "ORGN_ODNO": "original-order-1",
                  "KRX_FWDG_ORD_ORGNO": "00009",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "ORD_QTY": "3",
                  "TOT_CCLD_QTY": "0",
                  "RMN_QTY": "3",
                  "RVSE_CNCL_DVSN": "02",
                  "SLL_BUY_DVSN_NAME": "BUY",
                  "PRCS_STAT_NAME": "Processed"
                }
              ]
            }
            """.trimIndent(),
        )

        val items = adapter.findOrderHistory(historyQuery())
        val item = items.single()
        val status = items.findStatusFor(
            BrokerOrderStatusQuery(
                orderIntentId = UUID.randomUUID(),
                internalOrderId = UUID.randomUUID(),
                externalOrderId = "original-order-1",
                symbol = "TQQQ",
                side = OrderIntentSide.BUY,
                market = StockOrderMarket.OVERSEAS_US,
                submittedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals("cancel-order-1", item.externalOrderId)
        assertEquals("original-order-1", item.originalOrderId)
        assertEquals("00009", item.branchOrderNumber)
        assertEquals(BrokerOrderStatus.CANCELLED, item.toStatus().status)
        assertEquals(BrokerOrderStatus.CANCELLED, status.status)
        assertEquals("original-order-1", status.externalOrderId)
    }

    @Test
    fun `maps overseas original order number aliases for cancel status recovery`() {
        val adapter = overseasHistoryAdapter(
            """
            {
              "rt_cd": "0",
              "ctx_area_fk200": "",
              "ctx_area_nk200": "",
              "output": [
                {
                  "ORD_NO": "cancel-order-2",
                  "ORGN_ORD_NO": "original-order-2",
                  "KRX_FWDG_ORD_ORGNO": "00009",
                  "PDNO": "TQQQ",
                  "ORD_DT": "20260602",
                  "ORD_TMD": "093000",
                  "ORD_QTY": "3",
                  "TOT_CCLD_QTY": "0",
                  "RMN_QTY": "3",
                  "RVSE_CNCL_DVSN_CD": "02",
                  "SLL_BUY_DVSN_NAME": "BUY",
                  "PRCS_STAT_NAME": "Processed"
                }
              ]
            }
            """.trimIndent(),
        )

        val items = adapter.findOrderHistory(historyQuery())
        val item = items.single()
        val status = items.findStatusFor(
            BrokerOrderStatusQuery(
                orderIntentId = UUID.randomUUID(),
                internalOrderId = UUID.randomUUID(),
                externalOrderId = "original-order-2",
                symbol = "TQQQ",
                side = OrderIntentSide.BUY,
                market = StockOrderMarket.OVERSEAS_US,
                submittedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            ),
        )

        assertEquals("cancel-order-2", item.externalOrderId)
        assertEquals("original-order-2", item.originalOrderId)
        assertEquals(BrokerOrderStatus.CANCELLED, item.toStatus().status)
        assertEquals(BrokerOrderStatus.CANCELLED, status.status)
        assertEquals("original-order-2", status.externalOrderId)
    }

    @Test
    fun `throws submission unknown when transient error happens during order submit`() {
        val exchangeFunction = StubExchangeFunction(
            responses = listOf("temporary"),
            statuses = listOf(HttpStatus.SERVICE_UNAVAILABLE),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        assertFailsWith<BrokerOrderSubmissionUnknownException> {
            adapter.submitOrder(
                brokerCommand(
                    side = OrderIntentSide.BUY,
                    symbol = "TQQQ",
                    price = 112.5,
                    quantity = 3,
                    orderType = StockOrderType.LOC,
                    isMock = false,
                ),
            )
        }
        assertEquals(1, exchangeFunction.requests.size)
    }

    @Test
    fun `throws submission unknown when accepted order response has no broker order id`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderSubmissionUnknownException> {
            adapter.submitOrder(
                brokerCommand(
                    side = OrderIntentSide.BUY,
                    symbol = "TQQQ",
                    price = 112.5,
                    quantity = 3,
                    orderType = StockOrderType.LOC,
                    isMock = false,
                ),
            )
        }

        assertEquals(null, exception.externalOrderId)
        assertEquals(1, exchangeFunction.requests.size)
    }

    @Test
    fun `throws submission unknown when accepted order response has all zero broker order id`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("0000000000")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderSubmissionUnknownException> {
            adapter.submitOrder(
                brokerCommand(
                    side = OrderIntentSide.BUY,
                    symbol = "TQQQ",
                    price = 112.5,
                    quantity = 3,
                    orderType = StockOrderType.LOC,
                    isMock = false,
                ),
            )
        }

        assertEquals(null, exception.externalOrderId)
        assertEquals(1, exchangeFunction.requests.size)
    }

    @Test
    fun `throws broker rejected when stock order response has nonzero return code`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("1")
                        .setMsgCd("APBK001")
                        .setMsg1("insufficient buying power")
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderRejectedException> {
            adapter.submitOrder(
                brokerCommand(
                    side = OrderIntentSide.BUY,
                    symbol = "TQQQ",
                    price = 112.5,
                    quantity = 3,
                    orderType = StockOrderType.LOC,
                    isMock = false,
                ),
            )
        }

        assertEquals("1", exception.brokerReturnCode)
        assertEquals("APBK001", exception.brokerMessageCode)
        assertEquals("insufficient buying power", exception.brokerMessage)
        assertEquals("stock order rejected by broker: APBK001 insufficient buying power", exception.message)
        assertEquals(1, exchangeFunction.requests.size)
    }

    @Test
    fun `maps submitted stock order response branch order number`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setKRXFWDGORDORGNO("00001")
                                .setODNO("domestic-order-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.submitOrder(
            BrokerOrderCommand(
                internalOrderId = UUID.randomUUID(),
                market = StockOrderMarket.DOMESTIC,
                side = OrderIntentSide.BUY,
                symbol = "005930",
                orderType = StockOrderType.LIMIT,
                price = 70_000.0,
                quantity = 1,
                isMock = false,
            ),
        )

        assertEquals("domestic-order-1", submission.externalOrderId)
        assertEquals("00001", submission.branchOrderNumber)
    }

    @Test
    fun `ignores all zero submitted stock order branch order number`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setKRXFWDGORDORGNO("0000000000")
                                .setODNO("domestic-order-zero-branch")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.submitOrder(
            BrokerOrderCommand(
                internalOrderId = UUID.randomUUID(),
                market = StockOrderMarket.DOMESTIC,
                side = OrderIntentSide.BUY,
                symbol = "005930",
                orderType = StockOrderType.LIMIT,
                price = 70_000.0,
                quantity = 1,
                isMock = false,
            ),
        )

        assertEquals("domestic-order-zero-branch", submission.externalOrderId)
        assertEquals(null, submission.branchOrderNumber)
    }

    @Test
    fun `domestic cancel checks cancelable order before submitting cancel request`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk100": "",
                      "ctx_area_nk100": "",
                      "output": [
                        {
                          "ord_gno_brno": "00001",
                          "odno": "domestic-order-1",
                          "orgn_odno": "",
                          "pdno": "005930",
                          "psbl_qty": "3"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("cancel-order-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.DOMESTIC,
                symbol = "005930",
                originalOrderId = "domestic-order-1",
                branchOrderNumber = "00001",
                price = 0.0,
                quantity = 2,
                orderType = StockOrderType.LIMIT,
                isMock = false,
            ),
        )

        assertEquals("cancel-order-1", submission.externalOrderId)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("/open-api/trading/inquire-psbl-rvsecncl", exchangeFunction.requests[0].url().path)
        assertEquals("false", exchangeFunction.requests[0].queryValue("isMock"))
        assertEquals("1", exchangeFunction.requests[0].queryValue("inqrDvsn1"))
        assertEquals("0", exchangeFunction.requests[0].queryValue("inqrDvsn2"))
        assertEquals("/open-api/trading/order-rvsecncl", exchangeFunction.requests[1].url().path)
    }

    @Test
    fun `domestic cancel selects highest possible quantity from matching cancelable rows`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk100": "",
                      "ctx_area_nk100": "",
                      "output": [
                        {
                          "ord_gno_brno": "00001",
                          "odno": "domestic-order-1",
                          "pdno": "005930",
                          "psbl_qty": "0"
                        },
                        {
                          "ord_gno_brno": "00001",
                          "odno": "domestic-revised-order-1",
                          "orgn_odno": "domestic-order-1",
                          "pdno": "005930",
                          "psbl_qty": "2"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("cancel-order-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.DOMESTIC,
                symbol = "005930",
                originalOrderId = "domestic-order-1",
                branchOrderNumber = "00001",
                price = 0.0,
                quantity = 2,
                orderType = StockOrderType.LIMIT,
                isMock = false,
            ),
        )

        assertEquals("cancel-order-1", submission.externalOrderId)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("/open-api/trading/order-rvsecncl", exchangeFunction.requests[1].url().path)
    }

    @Test
    fun `domestic cancel matches cancelable row with missing branch when order id matches`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk100": "",
                      "ctx_area_nk100": "",
                      "output": [
                        {
                          "ord_gno_brno": "0000000000",
                          "odno": "domestic-order-1",
                          "pdno": "005930",
                          "psbl_qty": "2"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("cancel-order-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.DOMESTIC,
                symbol = "005930",
                originalOrderId = "domestic-order-1",
                branchOrderNumber = "00001",
                price = 0.0,
                quantity = 2,
                orderType = StockOrderType.LIMIT,
                isMock = false,
            ),
        )

        assertEquals("cancel-order-1", submission.externalOrderId)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("/open-api/trading/order-rvsecncl", exchangeFunction.requests[1].url().path)
    }

    @Test
    fun `domestic cancel uses cancelable lookup branch when command branch is missing`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk100": "",
                      "ctx_area_nk100": "",
                      "output": [
                        {
                          "ord_gno_brno": "00001",
                          "odno": "domestic-order-1",
                          "pdno": "005930",
                          "psbl_qty": "2"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("cancel-order-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.DOMESTIC,
                symbol = "005930",
                originalOrderId = "domestic-order-1",
                branchOrderNumber = null,
                price = 0.0,
                quantity = 2,
                orderType = StockOrderType.LIMIT,
                isMock = false,
            ),
        )

        assertEquals("cancel-order-1", submission.externalOrderId)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("/open-api/trading/order-rvsecncl", exchangeFunction.requests[1].url().path)
    }

    @Test
    fun `domestic cancel rejects when cancelable lookup cannot provide branch`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk100": "",
                      "ctx_area_nk100": "",
                      "output": [
                        {
                          "ord_gno_brno": "0000000000",
                          "odno": "domestic-order-1",
                          "pdno": "005930",
                          "psbl_qty": "2"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderRejectedException> {
            adapter.cancelOrder(
                brokerCancelCommand(
                    market = StockOrderMarket.DOMESTIC,
                    symbol = "005930",
                    originalOrderId = "domestic-order-1",
                    branchOrderNumber = null,
                    price = 0.0,
                    quantity = 2,
                    orderType = StockOrderType.LIMIT,
                    isMock = false,
                ),
            )
        }

        assertEquals("domestic cancelable order has no branch order number: domestic-order-1", exception.message)
        assertEquals(1, exchangeFunction.requests.size)
    }

    @Test
    fun `finds domestic cancelable orders without submitting cancel`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk100": "",
                      "ctx_area_nk100": "",
                      "output": [
                        {
                          "ord_gno_brno": "00002",
                          "odno": "other-domestic-order",
                          "pdno": "000660",
                          "psbl_qty": "1"
                        },
                        {
                          "ord_gno_brno": "00001",
                          "odno": "domestic-order-1",
                          "pdno": "005930",
                          "psbl_qty": "3"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val items = adapter.findCancelableOrders(
            BrokerOrderCancelableQuery(
                market = StockOrderMarket.DOMESTIC,
                symbol = "005930",
                originalOrderId = "domestic-order-1",
                branchOrderNumber = "00001",
                isMock = false,
            ),
        )

        assertEquals(1, items.size)
        with(items.single()) {
            assertEquals("domestic-order-1", orderId)
            assertEquals("00001", branchOrderNumber)
            assertEquals("005930", symbol)
            assertEquals(3L, possibleQuantity)
        }
        assertEquals(1, exchangeFunction.requests.size)
        assertEquals("/open-api/trading/inquire-psbl-rvsecncl", exchangeFunction.requests.single().url().path)
    }

    @Test
    fun `domestic cancelable lookup maps uppercase aliases and cursor pagination`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "RT_CD": "0",
                      "CTX_AREA_FK100": "FK1",
                      "CTX_AREA_NK100": "NK1",
                      "OUTPUT": [
                        {
                          "ORD_GNO_BRNO": "99999",
                          "ODNO": "other-order",
                          "PDNO": "005930",
                          "PSBL_QTY": "1"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                jsonResponse(
                    """
                    {
                      "RT_CD": "0",
                      "CTX_AREA_FK100": "",
                      "CTX_AREA_NK100": "",
                      "OUTPUT1": {
                        "ORD_GNO_BRNO": "00001",
                        "ODNO": "domestic-order-1",
                        "ORGN_ODNO": "",
                        "PDNO": "005930",
                        "PSBL_QTY": "3"
                      }
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("cancel-order-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.DOMESTIC,
                symbol = "005930",
                originalOrderId = "domestic-order-1",
                branchOrderNumber = "00001",
                price = 0.0,
                quantity = 2,
                orderType = StockOrderType.LIMIT,
                isMock = false,
            ),
        )

        assertEquals("cancel-order-1", submission.externalOrderId)
        assertEquals(3, exchangeFunction.requests.size)
        assertEquals("FK1", exchangeFunction.requests[1].queryValue("ctxAreaFk100"))
        assertEquals("NK1", exchangeFunction.requests[1].queryValue("ctxAreaNk100"))
        assertEquals("/open-api/trading/order-rvsecncl", exchangeFunction.requests[2].url().path)
    }

    @Test
    fun `domestic cancelable lookup maps revision cancel possible quantity aliases`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk100": "",
                      "ctx_area_nk100": "",
                      "output1": [
                        {
                          "ord_gno_brno": "00001",
                          "odno": "domestic-order-1",
                          "pdno": "005930",
                          "rvse_cncl_psbl_qty": "2"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("cancel-order-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.DOMESTIC,
                symbol = "005930",
                originalOrderId = "domestic-order-1",
                branchOrderNumber = "00001",
                price = 0.0,
                quantity = 2,
                orderType = StockOrderType.LIMIT,
                isMock = false,
            ),
        )

        assertEquals("cancel-order-1", submission.externalOrderId)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("/open-api/trading/order-rvsecncl", exchangeFunction.requests[1].url().path)
    }

    @Test
    fun `domestic cancelable lookup maps order identity aliases`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk100": "",
                      "ctx_area_nk100": "",
                      "output1": [
                        {
                          "krxFwdgOrdOrgno": "00001",
                          "ORDER_NO": "revised-domestic-order-1",
                          "orgnOdno": "domestic-order-1",
                          "PRDT_CODE": "005930",
                          "PSBL_RVSE_CNCL_QTY": "2"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("cancel-order-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.DOMESTIC,
                symbol = "005930",
                originalOrderId = "domestic-order-1",
                branchOrderNumber = "00001",
                price = 0.0,
                quantity = 2,
                orderType = StockOrderType.LIMIT,
                isMock = false,
            ),
        )

        assertEquals("cancel-order-1", submission.externalOrderId)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("/open-api/trading/order-rvsecncl", exchangeFunction.requests[1].url().path)
    }

    @Test
    fun `domestic cancelable lookup maps camel case order and quantity aliases`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rtCd": "0",
                      "ctxAreaFk100": "",
                      "ctxAreaNk100": "",
                      "output1": [
                        {
                          "ordOrgno": "00001",
                          "ordNo": "revised-domestic-order-1",
                          "orgnOdno": "domestic-order-1",
                          "prdtCode": "005930",
                          "psblQty": "2"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                protobufResponse(
                    ApiResponse.StockOrder.newBuilder()
                        .setRtCd("0")
                        .setOutput(
                            ApiResponse.Output.newBuilder()
                                .setODNO("cancel-order-1")
                                .build(),
                        )
                        .build(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val submission = adapter.cancelOrder(
            brokerCancelCommand(
                market = StockOrderMarket.DOMESTIC,
                symbol = "005930",
                originalOrderId = "domestic-order-1",
                branchOrderNumber = "00001",
                price = 0.0,
                quantity = 2,
                orderType = StockOrderType.LIMIT,
                isMock = false,
            ),
        )

        assertEquals("cancel-order-1", submission.externalOrderId)
        assertEquals(2, exchangeFunction.requests.size)
        assertEquals("/open-api/trading/order-rvsecncl", exchangeFunction.requests[1].url().path)
    }

    @Test
    fun `domestic cancelable lookup preserves uppercase kis business failure`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "RT_CODE": "1",
                      "MSG_CODE": "APBK0001",
                      "MSG": "cancelable lookup rejected"
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderQueryFailedException> {
            adapter.cancelOrder(
                brokerCancelCommand(
                    market = StockOrderMarket.DOMESTIC,
                    symbol = "005930",
                    originalOrderId = "domestic-order-1",
                    branchOrderNumber = "00001",
                    price = 0.0,
                    quantity = 2,
                    orderType = StockOrderType.LIMIT,
                    isMock = false,
                ),
            )
        }

        assertEquals("1", exception.brokerReturnCode)
        assertEquals("APBK0001", exception.brokerMessageCode)
        assertEquals("cancelable lookup rejected", exception.brokerMessage)
        assertEquals("domestic cancelable order lookup failed: APBK0001 cancelable lookup rejected", exception.message)
        assertEquals(1, exchangeFunction.requests.size)
    }

    @Test
    fun `domestic cancel rejects when possible quantity is lower than requested quantity`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk100": "",
                      "ctx_area_nk100": "",
                      "output": [
                        {
                          "ord_gno_brno": "00001",
                          "odno": "domestic-order-1",
                          "pdno": "005930",
                          "psbl_qty": "1"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderRejectedException> {
            adapter.cancelOrder(
                brokerCancelCommand(
                    market = StockOrderMarket.DOMESTIC,
                    symbol = "005930",
                    originalOrderId = "domestic-order-1",
                    branchOrderNumber = "00001",
                    price = 0.0,
                    quantity = 2,
                    orderType = StockOrderType.LIMIT,
                    isMock = false,
                ),
            )
        }

        assertEquals(
            "domestic order cancel quantity exceeds possible quantity: requested=2 possible=1",
            exception.message,
        )
        assertEquals(1, exchangeFunction.requests.size)
    }

    @Test
    fun `domestic cancel rejects when order is not in cancelable list`() {
        val exchangeFunction = ResponseExchangeFunction(
            responses = listOf(
                jsonResponse(
                    """
                    {
                      "rt_cd": "0",
                      "ctx_area_fk100": "",
                      "ctx_area_nk100": "",
                      "output": []
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
        )

        val exception = assertFailsWith<BrokerOrderRejectedException> {
            adapter.cancelOrder(
                brokerCancelCommand(
                    market = StockOrderMarket.DOMESTIC,
                    symbol = "005930",
                    originalOrderId = "domestic-order-1",
                    branchOrderNumber = "00001",
                    price = 0.0,
                    quantity = 2,
                    orderType = StockOrderType.LIMIT,
                    isMock = false,
                ),
            )
        }

        assertEquals("domestic order is not cancelable: domestic-order-1", exception.message)
        assertEquals(1, exchangeFunction.requests.size)
    }

    private fun overseasHistoryAdapter(response: String): KisBrokerGatewayAdapter {
        return KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(StubExchangeFunction(responses = listOf(response)))
                .build(),
        )
    }

    private fun domesticHistoryAdapter(
        response: DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersResponse,
    ): KisBrokerGatewayAdapter {
        return KisBrokerGatewayAdapter(
            WebClient.builder()
                .exchangeFunction(ResponseExchangeFunction(responses = listOf(protobufResponse(response))))
                .build(),
        )
    }

    private fun historyQuery(): BrokerOrderHistoryQuery {
        return BrokerOrderHistoryQuery(
            market = StockOrderMarket.OVERSEAS_US,
            symbol = "TQQQ",
            from = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            to = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            isMock = false,
        )
    }

    private fun domesticHistoryQuery(): BrokerOrderHistoryQuery {
        return BrokerOrderHistoryQuery(
            market = StockOrderMarket.DOMESTIC,
            symbol = "005930",
            from = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            to = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            isMock = false,
        )
    }

    private fun domesticHistoryResponse(
        vararg rows: DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1,
    ): DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersResponse {
        return DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersResponse.newBuilder()
            .setRtCd("0")
            .setMsgCd("")
            .setMsg1("")
            .addAllOutput1(rows.toList())
            .build()
    }

    private fun domesticRow(
        orderId: String = "domestic-order-1",
        executionId: String = "",
        orderedQuantity: String = "3",
        filledQuantity: String = "0",
        remainingQuantity: String = "3",
        rejectedQuantity: String = "0",
        cancelledQuantity: String = "0",
        cancelled: Boolean = false,
        side: OrderIntentSide = OrderIntentSide.BUY,
        averagePrice: String = "",
        orderedPrice: String = "",
        statusName: String = "",
        rejectionReason: String = "",
        rejectionReasonCode: String = "",
    ): DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1 {
        return DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1.newBuilder()
            .setOrdDt("20260602")
            .setOrdGnoBrno("00001")
            .setOdno(orderId)
            .setCcldNo(executionId)
            .setSllBuyDvsnCd(side.toKisSideCode())
            .setPdno("005930")
            .setPrdtName("Samsung Electronics")
            .setOrdQty(orderedQuantity)
            .setOrdUnpr(orderedPrice)
            .setOrdTmd("093000")
            .setTotCcldQty(filledQuantity)
            .setAvgPrvs(averagePrice)
            .setCnclYn(if (cancelled) "Y" else "N")
            .setCnclCfrmQty(cancelledQuantity)
            .setRmnQty(remainingQuantity)
            .setRjctQty(rejectedQuantity)
            .setPrcsStatName(statusName)
            .setRjctRsonName(rejectionReason)
            .setRjctRsonCd(rejectionReasonCode)
            .build()
    }

    private fun OrderIntentSide.toKisSideCode(): String {
        return when (this) {
            OrderIntentSide.SELL -> "01"
            OrderIntentSide.BUY -> "02"
        }
    }

    private fun brokerCommand(
        side: OrderIntentSide,
        symbol: String,
        exchange: String = "NASD",
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
            exchange = exchange,
            orderType = orderType,
            price = price,
            quantity = quantity,
            isMock = isMock,
        )
    }

    private fun brokerCancelCommand(
        market: StockOrderMarket,
        symbol: String,
        exchange: String = "NASD",
        originalOrderId: String,
        branchOrderNumber: String?,
        price: Double,
        quantity: Int,
        orderType: StockOrderType,
        cancelAll: Boolean = true,
        isMock: Boolean,
    ): BrokerOrderCancelCommand {
        return BrokerOrderCancelCommand(
            internalOrderId = UUID.randomUUID(),
            market = market,
            symbol = symbol,
            exchange = exchange,
            originalOrderId = originalOrderId,
            branchOrderNumber = branchOrderNumber,
            orderType = orderType,
            price = price,
            quantity = quantity,
            cancelAll = cancelAll,
            isMock = isMock,
        )
    }

    private class StubExchangeFunction(
        responses: List<String>,
        statuses: List<HttpStatus> = List(responses.size) { HttpStatus.OK },
    ) : ExchangeFunction {
        private val responses = ArrayDeque(responses)
        private val responseStatuses = ArrayDeque(statuses)
        val requests: MutableList<ClientRequest> = mutableListOf()

        override fun exchange(request: ClientRequest): Mono<ClientResponse> {
            requests += request
            return Mono.just(
                ClientResponse.create(responseStatuses.removeFirst())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(responses.removeFirst())
                    .build(),
            )
        }
    }

    private class ResponseExchangeFunction(
        responses: List<ClientResponse>,
    ) : ExchangeFunction {
        private val responses = ArrayDeque(responses)
        val requests: MutableList<ClientRequest> = mutableListOf()

        override fun exchange(request: ClientRequest): Mono<ClientResponse> {
            requests += request
            return Mono.just(responses.removeFirst())
        }
    }

    private fun protobufResponse(response: ApiResponse.StockOrder): ClientResponse {
        val dataBuffer = DefaultDataBufferFactory().wrap(response.toByteArray())
        return ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROTOBUF_VALUE)
            .body(Flux.just(dataBuffer))
            .build()
    }

    private fun jsonResponse(response: String): ClientResponse {
        return ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(response)
            .build()
    }

    private fun protobufResponse(
        response: DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersResponse,
    ): ClientResponse {
        val dataBuffer = DefaultDataBufferFactory().wrap(response.toByteArray())
        return ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROTOBUF_VALUE)
            .body(Flux.just(dataBuffer))
            .build()
    }

    private fun ClientRequest.queryValue(name: String): String? {
        return url().rawQuery
            ?.split("&")
            ?.mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.firstOrNull() == name) parts.getOrElse(1) { "" } else null
            }
            ?.firstOrNull()
    }
}

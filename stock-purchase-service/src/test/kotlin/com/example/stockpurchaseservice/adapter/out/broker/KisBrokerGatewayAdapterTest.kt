package com.example.stockpurchaseservice.adapter.out.broker

import ApiResponse
import DailyExecutionOrdersResponseOuterClass
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderRejectedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionUnknownException
import com.example.stockpurchaseservice.application.port.out.ExecutionQuantityModeDto
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
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
                from = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
                to = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
                isMock = false,
            ),
        )

        assertEquals("domestic-order-1", exchangeFunction.requests.single().queryValue("odno"))
        assertEquals("", exchangeFunction.requests.single().queryValue("pdno"))
        assertEquals("20260602", exchangeFunction.requests.single().queryValue("inqrStrtDt"))
        assertEquals("20260602", exchangeFunction.requests.single().queryValue("inqrEndDt"))
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
    fun `maps domestic partial fill as cumulative execution and submitted status`() {
        val adapter = domesticHistoryAdapter(
            domesticHistoryResponse(
                domesticRow(
                    orderId = "partial-domestic-order",
                    orderedQuantity = "3",
                    filledQuantity = "1",
                    remainingQuantity = "2",
                    averagePrice = "71200.5",
                ),
            ),
        )

        val item = adapter.findOrderHistory(domesticHistoryQuery()).single()
        val execution = item.toExecutionDto()

        assertEquals(BrokerOrderStatus.SUBMITTED, item.toStatus().status)
        checkNotNull(execution)
        assertEquals("005930", execution.stockId)
        assertEquals("partial-domestic-order", execution.externalOrderId)
        assertEquals(1, execution.quantity)
        assertEquals(ExecutionTypeDto.PURCHASE, execution.type)
        assertEquals(71200.5, execution.averageExecutionPrice)
        assertEquals(ExecutionQuantityModeDto.CUMULATIVE, execution.quantityMode)
    }

    @Test
    fun `maps domestic full fill as cumulative execution and submitted status`() {
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

        assertEquals(BrokerOrderStatus.SUBMITTED, item.toStatus().status)
        checkNotNull(execution)
        assertEquals("filled-domestic-order", execution.externalOrderId)
        assertEquals(3, execution.quantity)
        assertEquals(71300.0, execution.averageExecutionPrice)
        assertEquals(ExecutionQuantityModeDto.CUMULATIVE, execution.quantityMode)
    }

    @Test
    fun `overseas order status lookup leaves unsupported broker order id query blank`() {
        val query = BrokerOrderHistoryQuery(
            market = StockOrderMarket.OVERSEAS_US,
            symbol = "TQQQ",
            externalOrderId = "broker-order-1",
            from = ZonedDateTime.parse("2026-06-01T09:00:00+09:00"),
            to = ZonedDateTime.parse("2026-06-01T09:00:00+09:00"),
            isMock = false,
        )

        val params = query.toKisOverseasExecutionOrderQuery()

        assertEquals("", params["odno"])
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
    fun `maps overseas partial fill as cumulative execution and submitted status`() {
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

        assertEquals(BrokerOrderStatus.SUBMITTED, item.toStatus().status)
        checkNotNull(execution)
        assertEquals("partial-order", execution.externalOrderId)
        assertEquals(1, execution.quantity)
        assertEquals(112.5, execution.averageExecutionPrice)
        assertEquals(ExecutionQuantityModeDto.CUMULATIVE, execution.quantityMode)
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
                  "ORD_GNO_BRNO": "00002",
                  "PDNO": "TQQQ",
                  "PRDT_ENG_NAME": "ProShares UltraPro QQQ",
                  "ORD_DT": "20260602",
                  "THCO_ORD_TMD": "093500",
                  "ORD_QTY": "4",
                  "TOT_CCLD_QTY": "2",
                  "RMN_QTY": "2",
                  "SLL_BUY_DVSN_NAME": "BUY",
                  "AVG_PRVS": "113.75"
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
        assertEquals("stock order rejected by broker: APBK001 insufficient buying power", exception.message)
        assertEquals(1, exchangeFunction.requests.size)
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
        orderedQuantity: String = "3",
        filledQuantity: String = "0",
        remainingQuantity: String = "3",
        rejectedQuantity: String = "0",
        cancelledQuantity: String = "0",
        cancelled: Boolean = false,
        side: OrderIntentSide = OrderIntentSide.BUY,
        averagePrice: String = "",
    ): DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1 {
        return DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1.newBuilder()
            .setOrdDt("20260602")
            .setOrdGnoBrno("00001")
            .setOdno(orderId)
            .setSllBuyDvsnCd(side.toKisSideCode())
            .setPdno("005930")
            .setPrdtName("Samsung Electronics")
            .setOrdQty(orderedQuantity)
            .setOrdTmd("093000")
            .setTotCcldQty(filledQuantity)
            .setAvgPrvs(averagePrice)
            .setCnclYn(if (cancelled) "Y" else "N")
            .setCnclCfrmQty(cancelledQuantity)
            .setRmnQty(remainingQuantity)
            .setRjctQty(rejectedQuantity)
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

    private fun brokerCancelCommand(
        market: StockOrderMarket,
        symbol: String,
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

package com.example.stockpurchaseservice.adapter.out.broker

import ApiResponse
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionUnknownException
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
    fun `overseas order status lookup sends broker order id when present`() {
        val query = BrokerOrderHistoryQuery(
            market = StockOrderMarket.OVERSEAS_US,
            symbol = "TQQQ",
            externalOrderId = "broker-order-1",
            from = ZonedDateTime.parse("2026-06-01T09:00:00+09:00"),
            to = ZonedDateTime.parse("2026-06-01T09:00:00+09:00"),
            isMock = false,
        )

        val params = query.toKisOverseasExecutionOrderQuery()

        assertEquals("broker-order-1", params["odno"])
        assertEquals("TQQQ", params["pdno"])
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

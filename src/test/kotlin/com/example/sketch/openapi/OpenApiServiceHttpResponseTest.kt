package com.example.sketch.openapi

import com.example.sketch.configure.Property
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.test.runTest
import org.springframework.http.codec.HttpMessageWriter
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.mock.http.client.reactive.MockClientHttpRequest
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OpenApiServiceHttpResponseTest {

    @BeforeTest
    fun setUpProperties() {
        Property.BASE_URL = "https://real.kis.test"
        Property.APP_KEY = "real-app-key"
        Property.APP_SECRET = "real-app-secret"
        Property.MOCK_BASE_URL = "https://mock.kis.test"
        Property.MOCK_APP_KEY = "mock-app-key"
        Property.MOCK_APP_SECRET = "mock-app-secret"
        Property.MOCK_ACCOUNT = "00000000"
        Property.MOCK_ACCOUNT_TAIL = "01"
        Property.ACCOUNT = "00000000"
        Property.ACCOUNT_TAIL = "01"
    }

    @Test
    fun `preserves kis business error body from non successful http response`() = runTest {
        val exchangeFunction = SingleResponseExchangeFunction(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            body = """
            {
              "rt_cd": "1",
              "msg_cd": "KIS500",
              "msg1": "temporary broker-side rejection"
            }
            """.trimIndent(),
        )
        val service = openApiService(exchangeFunction)

        val response = service.getOverseasStockBalance(GetOverseasStockBalanceRequest(isMock = true))

        assertEquals("1", response.path("rt_cd").asText())
        assertEquals("KIS500", response.path("msg_cd").asText())
        assertEquals("/uapi/overseas-stock/v1/trading/inquire-balance", exchangeFunction.requests.single().url().path)
    }

    @Test
    fun `keeps non kis non successful http response as transport failure`() = runTest {
        val exchangeFunction = SingleResponseExchangeFunction(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            body = """
            {
              "error": "server failure"
            }
            """.trimIndent(),
        )
        val service = openApiService(exchangeFunction)

        val exception = assertFailsWith<RuntimeException> {
            service.getOverseasStockBalance(GetOverseasStockBalanceRequest(isMock = true))
        }

        assertEquals("500 INTERNAL_SERVER_ERROR", exception.message)
    }

    @Test
    fun `sends official tr id for real overseas us loc buy order`() = runTest {
        val exchangeFunction = SingleResponseExchangeFunction.success()
        val service = openApiService(exchangeFunction)

        service.postOverseasStockOrder(
            OverseasStockOrderRequest(
                PDNO = "tqqq",
                OVRS_EXCG_CD = "nasd",
                ORD_QTY = 1,
                OVRS_ORD_UNPR = "112.5",
                ORD_DVSN = "34",
                isMock = false,
            ),
        )

        with(exchangeFunction.requests.single()) {
            assertEquals("/uapi/overseas-stock/v1/trading/order", url().path)
            assertEquals("TTTT1002U", headers().getFirst("tr_id"))
            assertEquals("real-app-key", headers().getFirst("appkey"))
            assertEquals("real-app-secret", headers().getFirst("appsecret"))
            with(bodyAsJson()) {
                assertEquals("00000000", text("CANO"))
                assertEquals("01", text("ACNT_PRDT_CD"))
                assertEquals("NASD", text("OVRS_EXCG_CD"))
                assertEquals("TQQQ", text("PDNO"))
                assertEquals("1", text("ORD_QTY"))
                assertEquals("112.5", text("OVRS_ORD_UNPR"))
                assertEquals("34", text("ORD_DVSN"))
                assertEquals("", text("SLL_TYPE"))
                assertEquals("0", text("ORD_SVR_DVSN_CD"))
            }
        }
    }

    @Test
    fun `sends official tr id for mock overseas us sell order`() = runTest {
        val exchangeFunction = SingleResponseExchangeFunction.success()
        val service = openApiService(exchangeFunction)

        service.postOverseasStockOrder(
            OverseasStockOrderRequest(
                PDNO = "TQQQ",
                ORD_QTY = 1,
                OVRS_ORD_UNPR = "112.5",
                ORD_DVSN = "00",
                SLL_TYPE = "00",
                isMock = true,
            ),
        )

        with(exchangeFunction.requests.single()) {
            assertEquals("/uapi/overseas-stock/v1/trading/order", url().path)
            assertEquals("VTTT1006U", headers().getFirst("tr_id"))
            assertEquals("mock-app-key", headers().getFirst("appkey"))
            assertEquals("mock-app-secret", headers().getFirst("appsecret"))
            with(bodyAsJson()) {
                assertEquals("00000000", text("CANO"))
                assertEquals("01", text("ACNT_PRDT_CD"))
                assertEquals("TQQQ", text("PDNO"))
                assertEquals("1", text("ORD_QTY"))
                assertEquals("112.5", text("OVRS_ORD_UNPR"))
                assertEquals("00", text("SLL_TYPE"))
                assertEquals("00", text("ORD_DVSN"))
            }
        }
    }

    @Test
    fun `sends official tr id for mock overseas cancel order`() = runTest {
        val exchangeFunction = SingleResponseExchangeFunction.success()
        val service = openApiService(exchangeFunction)

        service.postOverseasStockOrderCancel(
            OverseasStockOrderCancelRequest(
                PDNO = "TQQQ",
                ORGN_ODNO = "broker-order-1",
                ORD_QTY = 1,
                isMock = true,
            ),
        )

        with(exchangeFunction.requests.single()) {
            assertEquals("/uapi/overseas-stock/v1/trading/order-rvsecncl", url().path)
            assertEquals("VTTT1004U", headers().getFirst("tr_id"))
            with(bodyAsJson()) {
                assertEquals("00000000", text("CANO"))
                assertEquals("01", text("ACNT_PRDT_CD"))
                assertEquals("NASD", text("OVRS_EXCG_CD"))
                assertEquals("TQQQ", text("PDNO"))
                assertEquals("broker-order-1", text("ORGN_ODNO"))
                assertEquals("02", text("RVSE_CNCL_DVSN_CD"))
                assertEquals("1", text("ORD_QTY"))
                assertEquals("0", text("OVRS_ORD_UNPR"))
                assertEquals("0", text("ORD_SVR_DVSN_CD"))
            }
        }
    }

    @Test
    fun `sends official tr id and query fields for mock overseas execution history`() = runTest {
        val exchangeFunction = SingleResponseExchangeFunction.success()
        val service = openApiService(exchangeFunction)

        service.getOverseasExecutionOrders(
            GetOverseasExecutionOrdersRequest(
                ordStrtDt = "20260601",
                ordEndDt = "20260602",
                pdno = "TQQQ",
                ovrsExcgCd = "NASD",
                isMock = true,
            ),
        )

        with(exchangeFunction.requests.single()) {
            assertEquals("/uapi/overseas-stock/v1/trading/inquire-ccnl", url().path)
            assertEquals("VTTS3035R", headers().getFirst("tr_id"))
            assertEquals("TQQQ", url().queryValue("PDNO"))
            assertEquals("20260601", url().queryValue("ORD_STRT_DT"))
            assertEquals("20260602", url().queryValue("ORD_END_DT"))
            assertEquals("NASD", url().queryValue("OVRS_EXCG_CD"))
        }
    }

    @Test
    fun `sends official chart price query and normalizes overseas fx rate`() = runTest {
        val exchangeFunction = SingleResponseExchangeFunction(
            status = HttpStatus.OK,
            body = """
            {
              "rt_cd": "0",
              "msg_cd": "MCA00000",
              "msg1": "ok",
              "output1": {
                "ovrs_nmix_prpr": "1,330.25",
                "stck_bsop_date": "20260602"
              },
              "output2": []
            }
            """.trimIndent(),
        )
        val service = openApiService(exchangeFunction)

        val response = service.getOverseasFxRate(
            GetOverseasFxRateRequest(
                isMock = true,
                marketDivCode = "X",
                symbol = "FX@KRW",
                fromDate = "20260601",
                toDate = "20260602",
            ),
        )

        assertEquals("FX@KRW", response.symbol)
        assertEquals("X", response.marketDivCode)
        assertEquals(1330.25, response.rate)
        assertEquals("20260602", response.observedDate)
        assertEquals("ovrs_nmix_prpr", response.rawField)
        with(exchangeFunction.requests.single()) {
            assertEquals("/uapi/overseas-price/v1/quotations/inquire-daily-chartprice", url().path)
            assertEquals("FHKST03030100", headers().getFirst("tr_id"))
            assertEquals("X", url().queryValue("FID_COND_MRKT_DIV_CODE"))
            assertEquals("FX@KRW", url().queryValue("FID_INPUT_ISCD"))
            assertEquals("20260601", url().queryValue("FID_INPUT_DATE_1"))
            assertEquals("20260602", url().queryValue("FID_INPUT_DATE_2"))
            assertEquals("D", url().queryValue("FID_PERIOD_DIV_CODE"))
        }
    }

    @Test
    fun `returns overseas fx diagnostic when chart response has no rate candidate`() = runTest {
        val exchangeFunction = SingleResponseExchangeFunction(
            status = HttpStatus.OK,
            body = """
            {
              "rt_cd": "0",
              "msg_cd": "MCA00000",
              "msg1": "ok",
              "output1": {
                "stck_bsop_date": "20260602",
                "unknown_price": "1,330.25"
              },
              "output2": [
                {
                  "stck_bsop_date": "20260601",
                  "another_price": "1,329.50"
                }
              ]
            }
            """.trimIndent(),
        )
        val service = openApiService(exchangeFunction)

        val response = service.getOverseasFxRate(
            GetOverseasFxRateRequest(
                isMock = true,
                marketDivCode = "X",
                symbol = "FX@KRW",
            ),
        )

        assertEquals("FX@KRW", response.symbol)
        assertEquals("X", response.marketDivCode)
        assertNull(response.rate)
        assertEquals("0", response.diagnostic?.returnCode)
        assertEquals("MCA00000", response.diagnostic?.messageCode)
        assertEquals("ok", response.diagnostic?.message)
        assertEquals(listOf("stck_bsop_date", "unknown_price"), response.diagnostic?.output1Fields)
        assertEquals(listOf("another_price", "stck_bsop_date"), response.diagnostic?.output2Fields)
    }

    @Test
    fun `sends official tr id and query fields for mock overseas balance`() = runTest {
        val exchangeFunction = SingleResponseExchangeFunction.success()
        val service = openApiService(exchangeFunction)

        service.getOverseasStockBalance(
            GetOverseasStockBalanceRequest(
                ovrsExcgCd = "NASD",
                trCrcyCd = "USD",
                isMock = true,
            ),
        )

        with(exchangeFunction.requests.single()) {
            assertEquals("/uapi/overseas-stock/v1/trading/inquire-balance", url().path)
            assertEquals("VTTS3012R", headers().getFirst("tr_id"))
            assertEquals("NASD", url().queryValue("OVRS_EXCG_CD"))
            assertEquals("USD", url().queryValue("TR_CRCY_CD"))
        }
    }

    @Test
    fun `sends official tr id and query fields for mock domestic balance`() = runTest {
        val exchangeFunction = SingleResponseExchangeFunction.success()
        val service = openApiService(exchangeFunction)

        service.getStockBalance(
            GetStockBalanceRequest(
                inqrDvsn = "02",
                ctxAreaFk100 = "FK1",
                ctxAreaNk100 = "NK1",
                isMock = true,
            ),
        )

        with(exchangeFunction.requests.single()) {
            assertEquals("/uapi/domestic-stock/v1/trading/inquire-balance", url().path)
            assertEquals("VTTC8434R", headers().getFirst("tr_id"))
            assertEquals("N", url().queryValue("AFHR_FLPR_YN"))
            assertEquals("02", url().queryValue("INQR_DVSN"))
            assertEquals("01", url().queryValue("UNPR_DVSN"))
            assertEquals("N", url().queryValue("FUND_STTL_ICLD_YN"))
            assertEquals("N", url().queryValue("FNCG_AMT_AUTO_RDPT_YN"))
            assertEquals("00", url().queryValue("PRCS_DVSN"))
            assertEquals("FK1", url().queryValue("CTX_AREA_FK100"))
            assertEquals("NK1", url().queryValue("CTX_AREA_NK100"))
        }
    }

    private fun openApiService(exchangeFunction: SingleResponseExchangeFunction): OpenApiService {
        val webClient = WebClient.builder()
            .baseUrl("https://mock.kis.test")
            .exchangeFunction(exchangeFunction)
            .build()
        return OpenApiService(
            webClient = webClient,
            mockWebClient = webClient,
            tokenCache = KisAccessTokenCache(
                properties = KisTokenProperties(),
                clock = TEST_CLOCK,
                tokenStore = PreloadedTokenStore(
                    KisTokenScope.MOCK to CachedKisAccessToken(
                        token = "mock-token",
                        expiresAt = TEST_NOW.plusSeconds(3600),
                    ),
                    KisTokenScope.REAL to CachedKisAccessToken(
                        token = "real-token",
                        expiresAt = TEST_NOW.plusSeconds(3600),
                    ),
                ),
            ),
        )
    }

    private class SingleResponseExchangeFunction(
        private val status: HttpStatus,
        private val body: String,
    ) : ExchangeFunction {
        val requests = mutableListOf<ClientRequest>()

        override fun exchange(request: ClientRequest): Mono<ClientResponse> {
            requests += request
            return Mono.just(
                ClientResponse.create(status)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build(),
            )
        }

        companion object {
            fun success(): SingleResponseExchangeFunction {
                return SingleResponseExchangeFunction(
                    status = HttpStatus.OK,
                    body = """
                    {
                      "rt_cd": "0",
                      "msg_cd": "MCA00000",
                      "msg1": "ok",
                      "output": {
                        "ODNO": "broker-order-1"
                      }
                    }
                    """.trimIndent(),
                )
            }
        }
    }

    private fun java.net.URI.queryValue(name: String): String? {
        return rawQuery.orEmpty()
            .split("&")
            .mapNotNull { part ->
                val pieces = part.split("=", limit = 2)
                pieces.firstOrNull()?.takeIf { it == name }?.let {
                    java.net.URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8)
                }
            }
            .firstOrNull()
    }

    private fun ClientRequest.bodyAsJson(): JsonNode {
        val mockRequest = MockClientHttpRequest(method(), url())
        mockRequest.headers.putAll(headers())
        body().insert(mockRequest, TEST_BODY_INSERTER_CONTEXT).block()
        val body = mockRequest.bodyAsString.block().orEmpty()
        return OBJECT_MAPPER.readTree(body)
    }

    private fun JsonNode.text(fieldName: String): String {
        return path(fieldName).asText()
    }

    private class PreloadedTokenStore(
        vararg tokens: Pair<KisTokenScope, CachedKisAccessToken>,
    ) : KisAccessTokenStore {
        private val tokens = tokens.toMap().toMutableMap()

        override fun load(scope: KisTokenScope): CachedKisAccessToken? = tokens[scope]

        override fun save(scope: KisTokenScope, token: CachedKisAccessToken) {
            tokens[scope] = token
        }

        override fun delete(scope: KisTokenScope) {
            tokens.remove(scope)
        }
    }

    private companion object {
        val TEST_NOW: Instant = Instant.parse("2026-06-02T00:00:00Z")
        val TEST_CLOCK: Clock = Clock.fixed(TEST_NOW, ZoneOffset.UTC)
        val OBJECT_MAPPER = jacksonObjectMapper()
        val TEST_BODY_INSERTER_CONTEXT = object : BodyInserter.Context {
            override fun messageWriters(): List<HttpMessageWriter<*>> {
                return ExchangeStrategies.withDefaults().messageWriters()
            }

            override fun serverRequest(): Optional<ServerHttpRequest> {
                return Optional.empty()
            }

            override fun hints(): Map<String, Any> {
                return emptyMap()
            }
        }
    }
}

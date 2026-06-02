package com.example.stockpurchaseservice.adapter.out.risk

import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FxRateAdapterTest {

    @Test
    fun `configured fx rate adapter returns configured rate and identity rate`() {
        val properties = OrderRiskProperties().apply {
            currencyConversion.ratesToBase["KRW"] = 0.001
        }
        val adapter = ConfiguredFxRateAdapter(properties)

        val krw = adapter.getRateToBase("krw", "usd")
        val usd = adapter.getRateToBase("USD", "USD")

        assertEquals("KRW", krw?.sourceCurrency)
        assertEquals("USD", krw?.baseCurrency)
        assertEquals(0.001, krw?.rateToBase)
        assertEquals("configured", krw?.provider)
        assertEquals(1.0, usd?.rateToBase)
    }

    @Test
    fun `configured fx rate adapter returns null for missing rate`() {
        val adapter = ConfiguredFxRateAdapter(OrderRiskProperties())

        assertNull(adapter.getRateToBase("KRW", "USD"))
    }

    @Test
    fun `configured fx rate adapter returns null when requested base differs from configured base`() {
        val properties = OrderRiskProperties().apply {
            currencyConversion.baseCurrency = "USD"
            currencyConversion.ratesToBase["KRW"] = 0.001
        }
        val adapter = ConfiguredFxRateAdapter(properties)

        assertNull(adapter.getRateToBase("KRW", "EUR"))
    }

    @Test
    fun `http fx rate adapter maps rate response and query parameters`() {
        val exchangeFunction = CapturingExchangeFunction(
            """
            {
              "rateToBase": "0.001",
              "provider": "kis-fx",
              "observedAt": "2026-06-02T10:00:00+09:00[Asia/Seoul]"
            }
            """.trimIndent(),
        )
        val properties = OrderRiskProperties.CurrencyConversionProperties().apply {
            http.path = "/fx/latest"
        }
        val adapter = HttpFxRateAdapter(
            webClient = WebClient.builder()
                .baseUrl("http://fx.example.test")
                .exchangeFunction(exchangeFunction)
                .build(),
            properties = properties,
        )

        val rate = adapter.getRateToBase("krw", "usd")

        assertEquals("KRW", rate?.sourceCurrency)
        assertEquals("USD", rate?.baseCurrency)
        assertEquals(0.001, rate?.rateToBase)
        assertEquals("kis-fx", rate?.provider)
        assertEquals("/fx/latest", exchangeFunction.requests.single().url().path)
        assertEquals("sourceCurrency=KRW&baseCurrency=USD", exchangeFunction.requests.single().url().query)
    }

    @Test
    fun `http fx rate adapter returns null when response has no positive rate`() {
        val adapter = HttpFxRateAdapter(
            webClient = WebClient.builder()
                .baseUrl("http://fx.example.test")
                .exchangeFunction(CapturingExchangeFunction("""{"rateToBase":"0"}"""))
                .build(),
            properties = OrderRiskProperties.CurrencyConversionProperties(),
        )

        assertNull(adapter.getRateToBase("KRW", "USD"))
    }

    @Test
    fun `kis wrapper fx rate adapter maps configured pair and inverts broker rate`() {
        val exchangeFunction = CapturingExchangeFunction(
            """
            {
              "symbol": "FX@KRW",
              "marketDivCode": "X",
              "rate": 1330.0,
              "observedDate": "20260602",
              "provider": "kis-overseas-daily-chartprice"
            }
            """.trimIndent(),
        )
        val properties = OrderRiskProperties.CurrencyConversionProperties().apply {
            kisWrapper.pairs["KRW-USD"] = OrderRiskProperties.KisWrapperFxRatePairProperties().apply {
                marketDivCode = "X"
                symbol = "FX@KRW"
                invert = true
                isMock = false
                fromDate = "20260601"
                toDate = "20260602"
            }
        }
        val adapter = KisWrapperFxRateAdapter(
            webClient = WebClient.builder()
                .baseUrl("http://kis-wrapper.test")
                .exchangeFunction(exchangeFunction)
                .build(),
            properties = properties,
        )

        val rate = adapter.getRateToBase("KRW", "USD")

        assertEquals("KRW", rate?.sourceCurrency)
        assertEquals("USD", rate?.baseCurrency)
        assertEquals(1.0 / 1330.0, rate?.rateToBase)
        assertEquals("kis-overseas-daily-chartprice", rate?.provider)
        assertEquals("/open-api/overseas/quotations/fx-rate", exchangeFunction.requests.single().url().path)
        assertEquals("X", exchangeFunction.requests.single().url().queryValue("marketDivCode"))
        assertEquals("FX@KRW", exchangeFunction.requests.single().url().queryValue("symbol"))
        assertEquals("false", exchangeFunction.requests.single().url().queryValue("isMock"))
        assertEquals("20260601", exchangeFunction.requests.single().url().queryValue("fromDate"))
        assertEquals("20260602", exchangeFunction.requests.single().url().queryValue("toDate"))
    }

    @Test
    fun `kis wrapper fx rate adapter surfaces diagnostic when response has no positive rate`() {
        val exchangeFunction = CapturingExchangeFunction(
            """
            {
              "symbol": "FX@KRW",
              "marketDivCode": "X",
              "rate": null,
              "diagnostic": {
                "returnCode": "0",
                "messageCode": "MCA00000",
                "message": "ok",
                "output1Fields": ["stck_bsop_date", "unknown_price"],
                "output2Fields": ["another_price", "stck_bsop_date"]
              }
            }
            """.trimIndent(),
        )
        val properties = OrderRiskProperties.CurrencyConversionProperties().apply {
            kisWrapper.pairs["KRW-USD"] = OrderRiskProperties.KisWrapperFxRatePairProperties().apply {
                marketDivCode = "X"
                symbol = "FX@KRW"
                invert = true
            }
        }
        val adapter = KisWrapperFxRateAdapter(
            webClient = WebClient.builder()
                .baseUrl("http://kis-wrapper.test")
                .exchangeFunction(exchangeFunction)
                .build(),
            properties = properties,
        )

        val exception = assertFailsWith<IllegalStateException> {
            adapter.getRateToBase("KRW", "USD")
        }

        assertTrue(exception.message.orEmpty().contains("returnCode=0"))
        assertTrue(exception.message.orEmpty().contains("messageCode=MCA00000"))
        assertTrue(exception.message.orEmpty().contains("unknown_price"))
        assertTrue(exception.message.orEmpty().contains("another_price"))
    }

    @Test
    fun `kis wrapper fx rate adapter returns null when pair is not configured`() {
        val adapter = KisWrapperFxRateAdapter(
            webClient = WebClient.builder()
                .baseUrl("http://kis-wrapper.test")
                .exchangeFunction(CapturingExchangeFunction("""{"rate":1330.0}"""))
                .build(),
            properties = OrderRiskProperties.CurrencyConversionProperties(),
        )

        assertNull(adapter.getRateToBase("KRW", "USD"))
    }

    private class CapturingExchangeFunction(
        private val body: String,
    ) : ExchangeFunction {
        val requests: MutableList<ClientRequest> = mutableListOf()

        override fun exchange(request: ClientRequest): Mono<ClientResponse> {
            requests += request
            return Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build(),
            )
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
}

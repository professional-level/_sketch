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
import kotlin.test.assertNull

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
}

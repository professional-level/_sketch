package com.example.sketch.openapi

import com.example.sketch.configure.Property
import kotlinx.coroutines.test.runTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    }
}

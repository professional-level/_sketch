package com.example.sketch.openapi.toss.adapter.out.api

import com.example.sketch.openapi.toss.application.port.`in`.TossAccountQuery
import com.example.sketch.openapi.toss.application.port.`in`.TossOrderCommand
import com.example.sketch.openapi.toss.application.port.`in`.TossQuery
import com.example.sketch.openapi.toss.domain.TossOpenApiConfigurationException
import com.example.sketch.openapi.toss.domain.TossOpenApiTokenException
import com.example.sketch.openapi.toss.domain.TossOrderSide
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.test.runTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.HttpMessageWriter
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.mock.http.client.reactive.MockClientHttpRequest
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TossOpenApiAdapterTest {

    @Test
    fun `issues oauth client credentials token`() = runTest {
        val exchangeFunction = RecordingExchangeFunction(tokenResponse())
        val adapter = adapter(exchangeFunction)

        val token = adapter.issueToken(forceRefresh = false)

        assertEquals("toss-access-token", token.accessToken)
        assertEquals("Bearer", token.tokenType)
        assertEquals(3600, token.expiresIn)
        assertEquals("trade", token.scope)
        with(exchangeFunction.requests.single()) {
            assertEquals("/oauth2/token", url().path)
            assertEquals(MediaType.APPLICATION_FORM_URLENCODED, headers().contentType!!)
            val body = bodyAsString()
            assertTrue(body.contains("grant_type=client_credentials"))
            assertTrue(body.contains("client_id=toss-client-id"))
            assertTrue(body.contains("client_secret=toss-client-secret"))
        }
    }

    @Test
    fun `sends cached bearer token account header and query for stock balance`() = runTest {
        val exchangeFunction = RecordingExchangeFunction(
            tokenResponse(),
            apiResponse(),
        )
        val adapter = adapter(exchangeFunction)

        val response = adapter.getStockBalance(
            TossAccountQuery(
                parameters = mapOf(
                    "symbol" to "TQQQ",
                    "market" to "US",
                ),
            ),
        )

        assertEquals(200, response.statusCode)
        with(exchangeFunction.requests[1]) {
            assertEquals("/stocks/balance", url().path)
            assertEquals("Bearer toss-access-token", headers().getFirst("Authorization"))
            assertEquals("toss-account-1", headers().getFirst("X-Tossinvest-Account"))
            assertEquals("TQQQ", url().queryValue("symbol"))
            assertEquals("US", url().queryValue("market"))
        }
    }

    @Test
    fun `selects sell simulation path and forwards raw order payload`() = runTest {
        val exchangeFunction = RecordingExchangeFunction(
            tokenResponse(),
            apiResponse(),
        )
        val adapter = adapter(exchangeFunction)

        adapter.simulateOrder(
            TossOrderCommand(
                side = TossOrderSide.SELL,
                accountNumber = "request-account",
                payload = mapOf(
                    "symbol" to "TQQQ",
                    "quantity" to 3,
                    "orderType" to "LIMIT",
                ),
            ),
        )

        with(exchangeFunction.requests[1]) {
            assertEquals("/orders/sell/simulations", url().path)
            assertEquals("Bearer toss-access-token", headers().getFirst("Authorization"))
            assertEquals("request-account", headers().getFirst("X-Tossinvest-Account"))
            with(bodyAsJson()) {
                assertEquals("TQQQ", path("symbol").asText())
                assertEquals(3, path("quantity").asInt())
                assertEquals("LIMIT", path("orderType").asText())
            }
        }
    }

    @Test
    fun `reuses cached token for subsequent calls`() = runTest {
        val exchangeFunction = RecordingExchangeFunction(
            tokenResponse(),
            apiResponse(),
            apiResponse(),
        )
        val adapter = adapter(exchangeFunction)

        adapter.getStockSnapshot(TossQuery(parameters = mapOf("symbol" to "TQQQ")))
        adapter.getStockSnapshot(TossQuery(parameters = mapOf("symbol" to "SOXL")))

        assertEquals(3, exchangeFunction.requests.size)
        assertEquals("/oauth2/token", exchangeFunction.requests[0].url().path)
        assertEquals("/stocks/snapshot", exchangeFunction.requests[1].url().path)
        assertEquals("/stocks/snapshot", exchangeFunction.requests[2].url().path)
    }

    @Test
    fun `invalidates cached token and retries once after unauthorized response`() = runTest {
        val exchangeFunction = RecordingExchangeFunction(
            tokenResponse(accessToken = "expired-token"),
            apiResponse(status = HttpStatus.UNAUTHORIZED, body = """{"error":"expired"}"""),
            tokenResponse(accessToken = "fresh-token"),
            apiResponse(),
        )
        val adapter = adapter(exchangeFunction)

        val response = adapter.getStockSnapshot(TossQuery(parameters = mapOf("symbol" to "TQQQ")))

        assertEquals(200, response.statusCode)
        assertEquals(4, exchangeFunction.requests.size)
        assertEquals("Bearer expired-token", exchangeFunction.requests[1].headers().getFirst("Authorization"))
        assertEquals("Bearer fresh-token", exchangeFunction.requests[3].headers().getFirst("Authorization"))
    }

    @Test
    fun `fails fast when operation path is not configured`() = runTest {
        val properties = properties().apply {
            paths.stockSnapshot = ""
        }
        val adapter = adapter(RecordingExchangeFunction(), properties)

        val exception = assertFailsWith<TossOpenApiConfigurationException> {
            adapter.getStockSnapshot(TossQuery(parameters = mapOf("symbol" to "TQQQ")))
        }

        assertTrue(exception.message.orEmpty().contains("akra.openapi.toss.paths.stock-snapshot"))
    }

    @Test
    fun `fails token request when credentials are not configured`() = runTest {
        val properties = properties().apply {
            clientSecret = ""
        }
        val adapter = adapter(RecordingExchangeFunction(), properties)

        val exception = assertFailsWith<TossOpenApiConfigurationException> {
            adapter.issueToken(forceRefresh = true)
        }

        assertTrue(exception.message.orEmpty().contains("akra.openapi.toss.client-secret"))
    }

    @Test
    fun `fails token request when response has no access token`() = runTest {
        val adapter = adapter(
            RecordingExchangeFunction(
                apiResponse(body = """{"token_type":"Bearer","expires_in":3600}"""),
            ),
        )

        val exception = assertFailsWith<TossOpenApiTokenException> {
            adapter.issueToken(forceRefresh = true)
        }

        assertTrue(exception.message.orEmpty().contains("access_token"))
    }

    private fun adapter(
        exchangeFunction: RecordingExchangeFunction,
        properties: TossOpenApiProperties = properties(),
    ): TossOpenApiAdapter {
        return TossOpenApiAdapter(
            webClient = WebClient.builder()
                .baseUrl(properties.baseUrl)
                .exchangeFunction(exchangeFunction)
                .build(),
            properties = properties,
            tokenCache = TossAccessTokenCache(properties),
            objectMapper = OBJECT_MAPPER,
        )
    }

    private fun properties(): TossOpenApiProperties {
        return TossOpenApiProperties().apply {
            baseUrl = "https://openapi.tossinvest.test"
            clientId = "toss-client-id"
            clientSecret = "toss-client-secret"
            defaultAccountNumber = "toss-account-1"
            paths.account = "/account"
            paths.stockSnapshot = "/stocks/snapshot"
            paths.stockBalance = "/stocks/balance"
            paths.buyOrder = "/orders/buy"
            paths.sellOrder = "/orders/sell"
            paths.buyOrderSimulation = "/orders/buy/simulations"
            paths.sellOrderSimulation = "/orders/sell/simulations"
        }
    }

    private data class RecordedResponse(
        val status: HttpStatus = HttpStatus.OK,
        val body: String = """
        {
          "ok": true
        }
        """.trimIndent(),
    )

    private class RecordingExchangeFunction(
        vararg responses: RecordedResponse,
    ) : ExchangeFunction {
        val requests = mutableListOf<ClientRequest>()
        private val responses = ArrayDeque(responses.toList())

        override fun exchange(request: ClientRequest): Mono<ClientResponse> {
            requests += request
            val response = if (responses.isEmpty()) RecordedResponse() else responses.removeFirst()
            return Mono.just(
                ClientResponse.create(response.status)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(response.body)
                    .build(),
            )
        }
    }

    private fun tokenResponse(
        accessToken: String = "toss-access-token",
        expiresIn: Long = 3600,
    ): RecordedResponse {
        return apiResponse(
            body = """
            {
              "access_token": "$accessToken",
              "token_type": "Bearer",
              "expires_in": $expiresIn,
              "scope": "trade"
            }
            """.trimIndent(),
        )
    }

    private fun apiResponse(
        status: HttpStatus = HttpStatus.OK,
        body: String = """
        {
          "ok": true
        }
        """.trimIndent(),
    ): RecordedResponse {
        return RecordedResponse(status = status, body = body)
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

    private fun ClientRequest.bodyAsString(): String {
        val mockRequest = MockClientHttpRequest(method(), url())
        mockRequest.headers.putAll(headers())
        body().insert(mockRequest, TEST_BODY_INSERTER_CONTEXT).block()
        return mockRequest.bodyAsString.block().orEmpty()
    }

    private fun ClientRequest.bodyAsJson(): JsonNode {
        return OBJECT_MAPPER.readTree(bodyAsString())
    }

    private companion object {
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

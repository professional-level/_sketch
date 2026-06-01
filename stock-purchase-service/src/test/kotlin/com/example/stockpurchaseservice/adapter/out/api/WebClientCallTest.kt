package com.example.stockpurchaseservice.adapter.out.api

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

class WebClientCallTest {

    @Test
    fun `retries transient get failures before returning response body`() {
        val exchangeFunction = SequentialExchangeFunction(
            responses = listOf(
                response(HttpStatus.SERVICE_UNAVAILABLE, "temporary"),
                response(HttpStatus.OK, "ok"),
            ),
        )
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()

        val result = client.getExternalApi(
            uri = "/test",
            queryParameters = emptyMap(),
            responseType = String::class.java,
            callOptions = ExternalApiCallOptions(
                timeout = Duration.ofSeconds(1),
                maxAttempts = 2,
                backoff = Duration.ZERO,
            ),
        )

        assertEquals("ok", result)
        assertEquals(2, exchangeFunction.requests.size)
    }

    @Test
    fun `does not retry non transient get failures`() {
        val exchangeFunction = SequentialExchangeFunction(
            responses = listOf(
                response(HttpStatus.BAD_REQUEST, "bad request"),
                response(HttpStatus.OK, "ok"),
            ),
        )
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()

        assertFailsWith<WebClientResponseException.BadRequest> {
            client.getExternalApi(
                uri = "/test",
                queryParameters = emptyMap(),
                responseType = String::class.java,
                callOptions = ExternalApiCallOptions(
                    timeout = Duration.ofSeconds(1),
                    maxAttempts = 2,
                    backoff = Duration.ZERO,
                ),
            )
        }
        assertEquals(1, exchangeFunction.requests.size)
    }

    private class SequentialExchangeFunction(
        responses: List<ClientResponse>,
    ) : ExchangeFunction {
        private val responses = ArrayDeque(responses)
        val requests: MutableList<ClientRequest> = mutableListOf()

        override fun exchange(request: ClientRequest): Mono<ClientResponse> {
            requests += request
            return Mono.just(responses.removeFirst())
        }
    }

    private fun response(status: HttpStatus, body: String): ClientResponse {
        return ClientResponse.create(status)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
            .body(body)
            .build()
    }
}

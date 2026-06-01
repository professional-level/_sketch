package com.example.stockpurchaseservice.adapter.out.observability

import com.example.stockpurchaseservice.config.observability.OperationalAlertProperties
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals

class OperationalAlertWebhookNotificationSinkTest {

    @Test
    fun `sends enabled webhook notification`() = runBlocking {
        val exchangeFunction = CapturingExchangeFunction()
        val sink = OperationalAlertWebhookNotificationSink(
            properties = enabledProperties(),
            webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction),
        )

        sink.send(notification())

        val request = exchangeFunction.requests.single()
        assertEquals(HttpMethod.POST, request.method())
        assertEquals("https://alerts.example.test/hooks/trading", request.url().toString())
    }

    @Test
    fun `does not send webhook when disabled`() = runBlocking {
        val exchangeFunction = CapturingExchangeFunction()
        val sink = OperationalAlertWebhookNotificationSink(
            properties = OperationalAlertProperties(),
            webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction),
        )

        sink.send(notification())

        assertEquals(emptyList(), exchangeFunction.requests)
    }

    @Test
    fun `does not propagate webhook delivery failure`() = runBlocking {
        val exchangeFunction = CapturingExchangeFunction(failure = IllegalStateException("network down"))
        val sink = OperationalAlertWebhookNotificationSink(
            properties = enabledProperties(),
            webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction),
        )

        sink.send(notification())

        assertEquals(1, exchangeFunction.requests.size)
    }

    private fun enabledProperties(): OperationalAlertProperties {
        return OperationalAlertProperties().apply {
            webhook.enabled = true
            webhook.url = "https://alerts.example.test/hooks/trading"
        }
    }

    private fun notification(): OperationalAlertNotification {
        return OperationalAlertNotification(
            type = "order_submission_failed",
            severity = "error",
            title = "Order submission failed",
            occurredAt = ZonedDateTime.parse("2026-06-01T09:00:00+09:00"),
            attributes = mapOf("symbol" to "TQQQ", "reason" to "broker timeout"),
        )
    }

    private class CapturingExchangeFunction(
        private val failure: RuntimeException? = null,
    ) : ExchangeFunction {
        val requests: MutableList<ClientRequest> = mutableListOf()

        override fun exchange(request: ClientRequest): Mono<ClientResponse> {
            requests += request
            failure?.let { return Mono.error(it) }
            return Mono.just(ClientResponse.create(HttpStatus.ACCEPTED).build())
        }
    }
}

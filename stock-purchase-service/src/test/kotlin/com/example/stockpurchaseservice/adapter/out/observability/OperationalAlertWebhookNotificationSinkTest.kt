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
    fun `sends enabled slack notification`() = runBlocking {
        val exchangeFunction = CapturingExchangeFunction()
        val sink = OperationalAlertWebhookNotificationSink(
            properties = OperationalAlertProperties().apply {
                slack.enabled = true
                slack.url = "https://hooks.slack.example.test/services/trading"
            },
            webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction),
        )

        sink.send(notification())

        val request = exchangeFunction.requests.single()
        assertEquals(HttpMethod.POST, request.method())
        assertEquals("https://hooks.slack.example.test/services/trading", request.url().toString())
    }

    @Test
    fun `sends enabled pagerduty notification`() = runBlocking {
        val exchangeFunction = CapturingExchangeFunction()
        val sink = OperationalAlertWebhookNotificationSink(
            properties = OperationalAlertProperties().apply {
                pagerDuty.enabled = true
                pagerDuty.routingKey = "routing-key"
                pagerDuty.url = "https://events.pagerduty.example.test/v2/enqueue"
            },
            webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction),
        )

        sink.send(notification())

        val request = exchangeFunction.requests.single()
        assertEquals(HttpMethod.POST, request.method())
        assertEquals("https://events.pagerduty.example.test/v2/enqueue", request.url().toString())
    }

    @Test
    fun `does not send pagerduty notification without routing key`() = runBlocking {
        val exchangeFunction = CapturingExchangeFunction()
        val sink = OperationalAlertWebhookNotificationSink(
            properties = OperationalAlertProperties().apply {
                pagerDuty.enabled = true
                pagerDuty.routingKey = ""
            },
            webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction),
        )

        sink.send(notification())

        assertEquals(emptyList(), exchangeFunction.requests)
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

    @Test
    fun `builds slack payload with alert fields`() {
        val properties = OperationalAlertProperties.Slack().apply {
            channel = "#trading-alerts"
            username = "akra-trading"
        }

        val payload = notification().toSlackPayload(properties)

        assertEquals("[ERROR] Order submission failed", payload.text)
        assertEquals("#trading-alerts", payload.channel)
        assertEquals("akra-trading", payload.username)
        assertEquals("danger", payload.attachments.single().color)
        assertEquals("TQQQ", payload.attachments.single().fields.single { it.title == "symbol" }.value)
        assertEquals("broker timeout", payload.attachments.single().fields.single { it.title == "reason" }.value)
    }

    @Test
    fun `builds pagerduty trigger payload with dedup key`() {
        val properties = OperationalAlertProperties.PagerDuty().apply {
            routingKey = "routing-key"
            source = "stock-purchase-service-live"
        }

        val payload = notification().toPagerDutyPayload(properties)

        assertEquals("routing-key", payload.routingKey)
        assertEquals("trigger", payload.eventAction)
        assertEquals("order_submission_failed:Order submission failed", payload.dedupKey)
        assertEquals("Order submission failed", payload.payload.summary)
        assertEquals("stock-purchase-service-live", payload.payload.source)
        assertEquals("error", payload.payload.severity)
        assertEquals("order_submission_failed", payload.payload.customDetails["type"])
        assertEquals("TQQQ", payload.payload.customDetails["symbol"])
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

package com.example.stockpurchaseservice.adapter.out.observability

import common.observability.TraceContext
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
    fun `builds webhook payload with trace context`() {
        val payload = notificationWithTrace().toWebhookPayload()

        assertEquals(TRACE_ID, payload.trace?.get("traceId"))
        assertEquals(SPAN_ID, payload.trace?.get("spanId"))
        assertEquals(TRACE_PARENT, payload.trace?.get("traceparent"))
    }

    @Test
    fun `builds slack payload with trace fields`() {
        val payload = notificationWithTrace().toSlackPayload(OperationalAlertProperties.Slack())

        val fields = payload.attachments.single().fields
        assertEquals(TRACE_ID, fields.single { it.title == "traceId" }.value)
        assertEquals(SPAN_ID, fields.single { it.title == "spanId" }.value)
        assertEquals(TRACE_PARENT, fields.single { it.title == "traceparent" }.value)
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

    @Test
    fun `builds pagerduty trigger payload with trace details`() {
        val properties = OperationalAlertProperties.PagerDuty().apply {
            routingKey = "routing-key"
        }

        val payload = notificationWithTrace().toPagerDutyPayload(properties)

        assertEquals(TRACE_ID, payload.payload.customDetails["traceId"])
        assertEquals(SPAN_ID, payload.payload.customDetails["spanId"])
        assertEquals(TRACE_PARENT, payload.payload.customDetails["traceparent"])
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

    private fun notificationWithTrace(): OperationalAlertNotification {
        return notification().copy(
            traceContext = TraceContext(
                traceId = TRACE_ID,
                spanId = SPAN_ID,
                traceParent = TRACE_PARENT,
            ),
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

    private companion object {
        const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        const val SPAN_ID = "00f067aa0ba902b7"
        const val TRACE_PARENT = "00-$TRACE_ID-$SPAN_ID-01"
    }
}

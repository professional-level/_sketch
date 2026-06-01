package com.example.stockpurchaseservice.adapter.out.observability

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.config.observability.OperationalAlertProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@ExternalApiAdapter
internal class OperationalAlertWebhookNotificationSink(
    private val properties: OperationalAlertProperties,
    webClientBuilder: WebClient.Builder,
) : OperationalAlertNotificationSink {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webClient = webClientBuilder.build()

    override suspend fun send(notification: OperationalAlertNotification) {
        sendGenericWebhook(notification)
        sendSlack(notification)
        sendPagerDuty(notification)
    }

    private suspend fun sendGenericWebhook(notification: OperationalAlertNotification) {
        val webhook = properties.webhook
        if (!webhook.enabled) return
        postJson("webhook", webhook.url, webhook.timeout, notification.toWebhookPayload(), notification)
    }

    private suspend fun sendSlack(notification: OperationalAlertNotification) {
        val slack = properties.slack
        if (!slack.enabled) return
        postJson("slack", slack.url, slack.timeout, notification.toSlackPayload(slack), notification)
    }

    private suspend fun sendPagerDuty(notification: OperationalAlertNotification) {
        val pagerDuty = properties.pagerDuty
        if (!pagerDuty.enabled) return
        val routingKey = pagerDuty.routingKey.trim()
        if (routingKey.isBlank()) {
            log.warn("Operational alert PagerDuty routing key is blank")
            return
        }
        postJson("pagerduty", pagerDuty.url, pagerDuty.timeout, notification.toPagerDutyPayload(pagerDuty), notification)
    }

    private suspend fun postJson(
        route: String,
        configuredUrl: String,
        timeout: Duration,
        payload: Any,
        notification: OperationalAlertNotification,
    ) {
        val url = configuredUrl.trim()
        if (url.isBlank()) {
            log.warn("Operational alert {} route is enabled but url is blank", route)
            return
        }

        runCatching {
            webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .timeout(timeout)
                .then()
                .awaitCompletion()
        }.onFailure { exception ->
            log.warn(
                "Failed to send operational alert {} notification: type={} severity={}",
                route,
                notification.type,
                notification.severity,
                exception,
            )
        }
    }

    private suspend fun Mono<Void>.awaitCompletion() {
        suspendCoroutine<Unit> { continuation ->
            subscribe(
                {},
                { exception -> continuation.resumeWithException(exception) },
                { continuation.resume(Unit) },
            )
        }
    }
}

internal fun OperationalAlertNotification.toWebhookPayload(): OperationalAlertWebhookPayload {
    return OperationalAlertWebhookPayload(
        type = type,
        severity = severity,
        title = title,
        occurredAt = occurredAt.toString(),
        attributes = nonNullAttributes(),
    )
}

internal fun OperationalAlertNotification.toSlackPayload(
    slack: OperationalAlertProperties.Slack,
): OperationalAlertSlackPayload {
    val fields = nonNullAttributes().map { (key, value) ->
        OperationalAlertSlackField(title = key, value = value, short = value.length <= 40)
    }
    val channel = slack.channel.trim().ifBlank { null }
    val username = slack.username.trim().ifBlank { null }
    return OperationalAlertSlackPayload(
        text = "[${severity.uppercase()}] $title",
        channel = channel,
        username = username,
        attachments = listOf(
            OperationalAlertSlackAttachment(
                color = severity.toSlackColor(),
                title = title,
                text = "type=$type severity=$severity occurredAt=$occurredAt",
                fields = fields,
            ),
        ),
    )
}

internal fun OperationalAlertNotification.toPagerDutyPayload(
    pagerDuty: OperationalAlertProperties.PagerDuty,
): OperationalAlertPagerDutyPayload {
    return OperationalAlertPagerDutyPayload(
        routingKey = pagerDuty.routingKey.trim(),
        dedupKey = dedupKey(),
        payload = OperationalAlertPagerDutyEventPayload(
            summary = title,
            source = pagerDuty.source.trim().ifBlank { "stock-purchase-service" },
            severity = severity.toPagerDutySeverity(),
            timestamp = occurredAt.toString(),
            customDetails = nonNullAttributes() + mapOf("type" to type),
        ),
    )
}

private fun OperationalAlertNotification.nonNullAttributes(): Map<String, String> {
    return attributes.filterValues { it != null }.mapValues { checkNotNull(it.value) }
}

private fun OperationalAlertNotification.dedupKey(): String {
    val id = attributes["orderIntentId"]
        ?: attributes["cancellationRequestId"]
        ?: attributes["externalExecutionId"]
        ?: attributes["externalOrderId"]
        ?: attributes["source"]
        ?: title
    return "$type:$id"
}

private fun String.toSlackColor(): String {
    return when (lowercase()) {
        "error" -> "danger"
        "warning" -> "warning"
        else -> "#439FE0"
    }
}

private fun String.toPagerDutySeverity(): String {
    return when (lowercase()) {
        "error" -> "error"
        "warning" -> "warning"
        else -> "info"
    }
}

internal data class OperationalAlertWebhookPayload(
    val type: String,
    val severity: String,
    val title: String,
    val occurredAt: String,
    val attributes: Map<String, String>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class OperationalAlertSlackPayload(
    val text: String,
    val channel: String?,
    val username: String?,
    val attachments: List<OperationalAlertSlackAttachment>,
)

internal data class OperationalAlertSlackAttachment(
    val color: String,
    val title: String,
    val text: String,
    val fields: List<OperationalAlertSlackField>,
)

internal data class OperationalAlertSlackField(
    val title: String,
    val value: String,
    val short: Boolean,
)

internal data class OperationalAlertPagerDutyPayload(
    @JsonProperty("routing_key")
    val routingKey: String,
    @JsonProperty("event_action")
    val eventAction: String = "trigger",
    @JsonProperty("dedup_key")
    val dedupKey: String,
    val payload: OperationalAlertPagerDutyEventPayload,
)

internal data class OperationalAlertPagerDutyEventPayload(
    val summary: String,
    val source: String,
    val severity: String,
    val timestamp: String,
    @JsonProperty("custom_details")
    val customDetails: Map<String, String>,
)

package com.example.stockpurchaseservice.adapter.out.observability

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.config.observability.OperationalAlertProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
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
        val webhook = properties.webhook
        if (!webhook.enabled) return
        val url = webhook.url.trim()
        if (url.isBlank()) {
            log.warn("Operational alert webhook is enabled but url is blank")
            return
        }

        runCatching {
            webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notification.toPayload())
                .retrieve()
                .toBodilessEntity()
                .timeout(webhook.timeout)
                .then()
                .awaitCompletion()
        }.onFailure { exception ->
            log.warn(
                "Failed to send operational alert webhook: type={} severity={}",
                notification.type,
                notification.severity,
                exception,
            )
        }
    }

    private fun OperationalAlertNotification.toPayload(): OperationalAlertWebhookPayload {
        return OperationalAlertWebhookPayload(
            type = type,
            severity = severity,
            title = title,
            occurredAt = occurredAt.toString(),
            attributes = attributes.filterValues { it != null }.mapValues { checkNotNull(it.value) },
        )
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

internal data class OperationalAlertWebhookPayload(
    val type: String,
    val severity: String,
    val title: String,
    val occurredAt: String,
    val attributes: Map<String, String>,
)

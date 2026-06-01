package com.example.stockpurchaseservice.adapter.out.api

import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.TimeoutException

private val EXTERNAL_API_TIMEOUT: Duration = Duration.ofSeconds(5)

internal data class ExternalApiCallOptions(
    val timeout: Duration = EXTERNAL_API_TIMEOUT,
    val maxAttempts: Int = 1,
    val backoff: Duration = Duration.ZERO,
    val transientHttpStatuses: Set<Int> = setOf(429, 500, 502, 503, 504),
) {
    init {
        require(!timeout.isNegative && !timeout.isZero) { "timeout must be positive" }
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(!backoff.isNegative) { "backoff must not be negative" }
    }
}

internal fun <T : Any> WebClient.getExternalApi(
    uri: String,
    queryParameters: Map<String, String>,
    responseType: Class<T>,
    accept: MediaType = MediaType.APPLICATION_JSON,
    callOptions: ExternalApiCallOptions = ExternalApiCallOptions(),
): T {
    val response = get()
        .uri { builder ->
            val uriBuilder = builder.path(uri)
            queryParameters.forEach { (key, value) -> uriBuilder.queryParam(key, value) }
            uriBuilder.build()
        }
        .accept(accept)
        .retrieve()
        .toEntity(responseType)
        .awaitExternalApi(callOptions)
        ?: throw RuntimeException("external api response is empty: $uri")

    return response.body ?: throw RuntimeException("external api body is empty: $uri")
}

internal fun <T> Mono<T>.awaitExternalApi(
    callOptions: ExternalApiCallOptions = ExternalApiCallOptions(),
): T? {
    var attempts = 0
    var lastFailure: Throwable? = null

    while (attempts < callOptions.maxAttempts) {
        attempts += 1
        try {
            return timeout(callOptions.timeout)
                .subscribeOn(Schedulers.boundedElastic())
                .block(callOptions.timeout)
        } catch (exception: Throwable) {
            lastFailure = exception
            val shouldRetry = attempts < callOptions.maxAttempts &&
                exception.isTransientExternalApiFailure(callOptions.transientHttpStatuses)
            if (!shouldRetry) throw exception
            if (!callOptions.backoff.isZero) {
                sleep(callOptions.backoff)
            }
        }
    }

    throw lastFailure ?: IllegalStateException("external api call failed without an exception")
}

private fun sleep(duration: Duration) {
    try {
        Thread.sleep(duration.toMillis())
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        throw exception
    }
}

internal fun Throwable.isTransientExternalApiFailure(transientHttpStatuses: Set<Int>): Boolean {
    val causeChain = generateSequence(this) { it.cause }.toList()
    return causeChain.any { cause ->
        when (cause) {
            is TimeoutException -> true
            is WebClientRequestException -> true
            is WebClientResponseException -> cause.statusCode.value() in transientHttpStatuses
            else -> false
        }
    }
}

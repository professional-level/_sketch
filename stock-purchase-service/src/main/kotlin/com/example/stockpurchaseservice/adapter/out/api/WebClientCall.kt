package com.example.stockpurchaseservice.adapter.out.api

import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

private val EXTERNAL_API_TIMEOUT: Duration = Duration.ofSeconds(5)

internal fun <T : Any> WebClient.getExternalApi(
    uri: String,
    queryParameters: Map<String, String>,
    responseType: Class<T>,
    accept: MediaType = MediaType.APPLICATION_JSON,
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
        .awaitExternalApi()
        ?: throw RuntimeException("external api response is empty: $uri")

    return response.body ?: throw RuntimeException("external api body is empty: $uri")
}

internal fun <T> Mono<T>.awaitExternalApi(): T? {
    return timeout(EXTERNAL_API_TIMEOUT)
        .subscribeOn(Schedulers.boundedElastic())
        .block(EXTERNAL_API_TIMEOUT)
}

package com.example.stocksearchservice.adapter.out.api.handler

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

private val EXTERNAL_API_TIMEOUT: Duration = Duration.ofSeconds(5)

internal fun <T> Mono<T>.awaitExternalApi(): T? {
    return timeout(EXTERNAL_API_TIMEOUT)
        .subscribeOn(Schedulers.boundedElastic())
        .block(EXTERNAL_API_TIMEOUT)
}

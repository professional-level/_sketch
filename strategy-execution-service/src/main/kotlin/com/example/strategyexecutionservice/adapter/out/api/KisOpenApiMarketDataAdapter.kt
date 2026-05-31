package com.example.strategyexecutionservice.adapter.out.api

import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.out.MarketDataPort
import com.example.strategyexecutionservice.application.port.out.StrategyMarketDataSnapshot
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

@ExternalApiAdapter
internal class KisOpenApiMarketDataAdapter(
    @Qualifier("kisOpenApiClient") private val kisOpenApiClient: WebClient,
) : MarketDataPort {

    override suspend fun getMarketSnapshot(
        symbol: String,
        recentCloseCount: Int,
    ): StrategyMarketDataSnapshot {
        val response = kisOpenApiClient.get()
            .uri { builder ->
                builder
                    .path("/open-api/overseas/quotations/dailyprice/{symbol}")
                    .queryParam("exchange", exchangeFor(symbol))
                    .queryParam("count", recentCloseCount)
                    .build(symbol.uppercase())
            }
            .retrieve()
            .bodyToMono(OverseasDailyPriceResponse::class.java)
            .awaitExternalApi()
            ?: throw IllegalStateException("empty overseas daily price response for symbol=$symbol")

        return StrategyMarketDataSnapshot(
            previousClose = response.previousClose,
            recentClosePrices = response.recentClosePrices,
        )
    }

    private fun exchangeFor(symbol: String): String {
        return when (symbol.uppercase()) {
            "TQQQ", "SOXL" -> "NAS"
            else -> "NAS"
        }
    }
}

@Configuration
internal class KisOpenApiMarketDataConfiguration {
    @Bean
    fun kisOpenApiClient(
        @Value("\${akra.market-data.kis-open-api.base-url:http://localhost:8079}") baseUrl: String,
    ): WebClient {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .build()
    }
}

private data class OverseasDailyPriceResponse(
    val previousClose: Double,
    val recentClosePrices: List<Double>,
)

private val EXTERNAL_API_TIMEOUT: Duration = Duration.ofSeconds(5)

private fun <T> Mono<T>.awaitExternalApi(): T? {
    return timeout(EXTERNAL_API_TIMEOUT)
        .subscribeOn(Schedulers.boundedElastic())
        .block(EXTERNAL_API_TIMEOUT)
}

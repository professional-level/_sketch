package com.example.strategyexecutionservice.adapter.out.api

import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals

class KisOpenApiMarketDataAdapterTest {

    @Test
    fun `loads overseas daily prices from kis open api wrapper`() = runBlocking {
        var requestedPath: String? = null
        val webClient = WebClient.builder()
            .exchangeFunction(
                ExchangeFunction { request ->
                    requestedPath = request.url().rawPath + "?" + request.url().rawQuery
                    Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(
                                """
                                {
                                  "symbol": "TQQQ",
                                  "exchange": "NAS",
                                  "previousClose": 100.0,
                                  "recentClosePrices": [100.0, 99.5, 98.5, 101.0, 102.0]
                                }
                                """.trimIndent(),
                            )
                            .build(),
                    )
                },
            )
            .build()
        val adapter = KisOpenApiMarketDataAdapter(webClient)

        val result = adapter.getMarketSnapshot("tqqq", recentCloseCount = 5)

        assertEquals("/open-api/overseas/quotations/dailyprice/TQQQ?exchange=NAS&count=5", requestedPath)
        assertEquals(100.0, result.previousClose)
        assertEquals(listOf(100.0, 99.5, 98.5, 101.0, 102.0), result.recentClosePrices)
    }
}

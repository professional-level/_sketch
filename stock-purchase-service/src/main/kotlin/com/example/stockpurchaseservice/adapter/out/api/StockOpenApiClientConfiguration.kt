package com.example.stockpurchaseservice.adapter.out.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
internal class StockOpenApiClientConfiguration {
    @Bean
    fun stockApiClient(
        @Value("\${akra.order.kis-open-api.base-url:http://localhost:8079}") baseUrl: String,
    ): WebClient {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .build()
    }
}

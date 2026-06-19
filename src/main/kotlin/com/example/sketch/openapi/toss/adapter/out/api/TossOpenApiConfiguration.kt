package com.example.sketch.openapi.toss.adapter.out.api

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(TossOpenApiProperties::class)
class TossOpenApiConfiguration {

    @Bean("tossOpenApiWebClient")
    fun tossOpenApiWebClient(properties: TossOpenApiProperties): WebClient {
        return WebClient.builder()
            .baseUrl(properties.baseUrl)
            .build()
    }
}

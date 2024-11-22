package com.example.sketch.configure.webclient

import com.example.sketch.configure.Property.Companion.BASE_URL
import com.example.sketch.configure.Property.Companion.MOCK_BASE_URL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {
    @Bean("webClient")
    fun webclient(): WebClient {
        return WebClient.builder()
            .baseUrl(BASE_URL)
            .build()
    }

    @Bean("mockWebclient")
    fun mockWebclient(): WebClient {
        return WebClient.builder()
            .baseUrl(MOCK_BASE_URL)
            .build()
    }
}

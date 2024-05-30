package com.example.sketch.configure.webclient

import com.example.sketch.configure.Property
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {
    @Bean
    fun webclient(): WebClient {
        return WebClient.builder()
            .baseUrl(Property.BASE_URL)
            .build()
    }
}
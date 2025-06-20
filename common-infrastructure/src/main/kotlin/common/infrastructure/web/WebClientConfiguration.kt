package com.example.common.infrastructure.web

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

/**
 * 공통 WebClient 설정
 * 모든 서비스에서 재사용할 수 있는 WebClient 설정
 */
@Configuration
class WebClientConfiguration {

    @Bean
    fun defaultWebClient(): WebClient {
        return WebClient.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) // 10MB
            }
            .build()
    }

    @Bean
    fun timeoutWebClient(): WebClient {
        return WebClient.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) // 10MB
            }
            .build()
    }
}

/**
 * WebClient 설정 프로퍼티
 */
@ConfigurationProperties(prefix = "webclient")
data class WebClientProperties(
    val timeout: Duration = Duration.ofSeconds(30),
    val maxInMemorySize: Int = 10 * 1024 * 1024, // 10MB
    val maxConnections: Int = 100,
    val baseUrls: Map<String, String> = emptyMap()
)
package com.example.sketch.openapi

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(KisTokenProperties::class)
class KisTokenConfiguration {

    @Bean
    fun kisAccessTokenCache(properties: KisTokenProperties): KisAccessTokenCache {
        return KisAccessTokenCache(properties)
    }
}

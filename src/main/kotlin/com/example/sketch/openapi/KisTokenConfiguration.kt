package com.example.sketch.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

@Configuration
@EnableConfigurationProperties(KisTokenProperties::class)
class KisTokenConfiguration {

    @Bean
    fun kisAccessTokenStore(
        properties: KisTokenProperties,
        objectMapper: ObjectMapper,
    ): KisAccessTokenStore {
        if (!properties.persistence.enabled) {
            return NoopKisAccessTokenStore
        }
        val path = properties.persistence.file
            .trim()
            .takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: defaultKisAccessTokenStorePath()
        return FileKisAccessTokenStore(path, objectMapper)
    }

    @Bean
    fun kisAccessTokenCache(
        properties: KisTokenProperties,
        tokenStore: KisAccessTokenStore,
    ): KisAccessTokenCache {
        return KisAccessTokenCache(properties, tokenStore = tokenStore)
    }

    private fun defaultKisAccessTokenStorePath(): Path {
        return Path.of(System.getProperty("user.home"), ".akra", "kis-token-cache.json")
    }
}

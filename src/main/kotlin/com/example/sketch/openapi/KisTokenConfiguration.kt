package com.example.sketch.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Path

@Configuration
@EnableConfigurationProperties(KisTokenProperties::class)
class KisTokenConfiguration {

    @Bean
    fun kisAccessTokenStore(
        properties: KisTokenProperties,
        objectMapper: ObjectMapper,
        jdbcTemplateProvider: ObjectProvider<JdbcTemplate>,
    ): KisAccessTokenStore {
        if (!properties.persistence.enabled) {
            return NoopKisAccessTokenStore
        }
        return when (properties.persistence.type.trim().lowercase()) {
            "file" -> FileKisAccessTokenStore(properties.persistence.filePath(), objectMapper)
            "jdbc" -> JdbcKisAccessTokenStore(
                jdbcTemplateProvider.getIfAvailable()
                    ?: error("akra.kis.token.persistence.type=jdbc requires a JdbcTemplate bean"),
            )
            else -> error(
                "Unsupported akra.kis.token.persistence.type=${properties.persistence.type}. " +
                    "Supported values are file and jdbc.",
            )
        }
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

    private fun KisTokenProperties.Persistence.filePath(): Path {
        return file
            .trim()
            .takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: defaultKisAccessTokenStorePath()
    }
}

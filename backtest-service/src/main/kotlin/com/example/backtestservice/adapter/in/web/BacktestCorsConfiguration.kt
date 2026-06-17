package com.example.backtestservice.adapter.`in`.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration(proxyBeanMethods = false)
class BacktestCorsConfiguration(
    @Value("\${akra.backtest.web.cors.allowed-origins:http://127.0.0.1:5184,http://localhost:5184}")
    private val allowedOrigins: String,
) {
    @Bean
    fun backtestCorsWebFilter(): CorsWebFilter {
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/backtests/**", corsConfiguration())
        return CorsWebFilter(source)
    }

    internal fun corsConfiguration(): CorsConfiguration {
        return CorsConfiguration().apply {
            allowedOrigins = parseAllowedOrigins()
            allowedMethods = listOf("GET", "POST", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = false
            maxAge = 3600L
        }
    }

    private fun parseAllowedOrigins(): List<String> {
        return allowedOrigins
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}

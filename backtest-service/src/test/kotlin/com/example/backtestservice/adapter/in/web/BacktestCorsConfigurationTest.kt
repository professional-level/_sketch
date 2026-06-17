package com.example.backtestservice.adapter.`in`.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BacktestCorsConfigurationTest {
    @Test
    fun `allows local laor dashboard origins`() {
        val config = BacktestCorsConfiguration(
            allowedOrigins = "http://127.0.0.1:5184, http://localhost:5184",
        ).corsConfiguration()

        assertEquals(
            listOf("http://127.0.0.1:5184", "http://localhost:5184"),
            config.allowedOrigins,
        )
        assertEquals(listOf("GET", "POST", "OPTIONS"), config.allowedMethods)
        assertEquals(listOf("*"), config.allowedHeaders)
        assertFalse(config.allowCredentials ?: true)
        assertEquals(3600L, config.maxAge)
    }
}

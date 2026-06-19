package com.example.sketch.openapi.toss.adapter.out.api

import com.example.sketch.openapi.toss.application.port.`in`.TossAccessTokenResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TossAccessTokenCacheTest {

    @Test
    fun `reuses cached token before refresh window`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-19T00:00:00Z"))
        val cache = TossAccessTokenCache(properties(), clock)
        var fetchCount = 0

        val first = cache.getOrRefresh {
            fetchCount += 1
            tokenResult("token-1", expiresIn = 3600)
        }
        val second = cache.getOrRefresh {
            fetchCount += 1
            tokenResult("token-2", expiresIn = 3600)
        }

        assertEquals("token-1", first.accessToken)
        assertEquals("token-1", second.accessToken)
        assertEquals(1, fetchCount)
        assertEquals(Instant.parse("2026-06-19T01:00:00Z"), first.expiresAt)
    }

    @Test
    fun `refreshes token inside refresh window`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-19T00:00:00Z"))
        val cache = TossAccessTokenCache(properties(), clock)
        var fetchCount = 0

        val first = cache.getOrRefresh {
            fetchCount += 1
            tokenResult("token-1", expiresIn = 600)
        }
        clock.advance(Duration.ofMinutes(1))
        val second = cache.getOrRefresh {
            fetchCount += 1
            tokenResult("token-2", expiresIn = 600)
        }

        assertEquals("token-1", first.accessToken)
        assertEquals("token-2", second.accessToken)
        assertEquals(2, fetchCount)
    }

    @Test
    fun `uses fallback ttl when token response omits expires in`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-19T00:00:00Z"))
        val cache = TossAccessTokenCache(
            properties().apply {
                token.fallbackTtl = Duration.ofMinutes(55)
            },
            clock,
        )

        val token = cache.getOrRefresh {
            tokenResult("fallback-token", expiresIn = null)
        }

        assertEquals("fallback-token", token.accessToken)
        assertEquals(Instant.parse("2026-06-19T00:55:00Z"), token.expiresAt)
    }

    @Test
    fun `fetches token once for concurrent cache misses`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-19T00:00:00Z"))
        val cache = TossAccessTokenCache(properties(), clock)
        var fetchCount = 0

        val results = List(20) {
            async {
                cache.getOrRefresh {
                    fetchCount += 1
                    delay(10)
                    tokenResult("shared-token", expiresIn = 3600)
                }
            }
        }.awaitAll()

        assertEquals(1, fetchCount)
        assertEquals(setOf("shared-token"), results.map { it.accessToken }.toSet())
    }

    private fun properties(): TossOpenApiProperties {
        return TossOpenApiProperties().apply {
            token.refreshBeforeExpiry = Duration.ofMinutes(10)
            token.fallbackTtl = Duration.ofMinutes(55)
        }
    }

    private fun tokenResult(accessToken: String, expiresIn: Long?): TossAccessTokenResult {
        return TossAccessTokenResult(
            accessToken = accessToken,
            tokenType = "Bearer",
            expiresIn = expiresIn,
            scope = "trade",
            rawBody = emptyMap<String, Any>(),
        )
    }

    private class MutableClock(
        private var instant: Instant,
    ) : Clock() {
        override fun instant(): Instant = instant
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this

        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }
    }
}

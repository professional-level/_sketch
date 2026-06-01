package com.example.sketch.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KisAccessTokenCacheTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `reuses cached token before refresh window`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-02T00:00:00Z"))
        val cache = KisAccessTokenCache(
            KisTokenProperties().apply {
                refreshBeforeExpiry = Duration.ofMinutes(10)
            },
            clock,
        )
        var fetchCount = 0

        val first = cache.getOrRefresh(KisTokenScope.REAL) {
            fetchCount += 1
            tokenResponse("token-1", expiresIn = 3600)
        }
        val second = cache.getOrRefresh(KisTokenScope.REAL) {
            fetchCount += 1
            tokenResponse("token-2", expiresIn = 3600)
        }

        assertEquals("token-1", first.token)
        assertEquals("token-1", second.token)
        assertEquals(1, fetchCount)
    }

    @Test
    fun `refreshes token inside refresh window`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-02T00:00:00Z"))
        val cache = KisAccessTokenCache(
            KisTokenProperties().apply {
                refreshBeforeExpiry = Duration.ofMinutes(10)
            },
            clock,
        )
        var fetchCount = 0

        val first = cache.getOrRefresh(KisTokenScope.REAL) {
            fetchCount += 1
            tokenResponse("token-1", expiresIn = 600)
        }
        clock.advance(Duration.ofMinutes(1))
        val second = cache.getOrRefresh(KisTokenScope.REAL) {
            fetchCount += 1
            tokenResponse("token-2", expiresIn = 600)
        }

        assertEquals("token-1", first.token)
        assertEquals("token-2", second.token)
        assertEquals(2, fetchCount)
    }

    @Test
    fun `keeps real and mock token scopes separate`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-02T00:00:00Z"))
        val cache = KisAccessTokenCache(KisTokenProperties(), clock)

        val real = cache.getOrRefresh(KisTokenScope.REAL) {
            tokenResponse("real-token", expiresIn = 3600)
        }
        val mock = cache.getOrRefresh(KisTokenScope.MOCK) {
            tokenResponse("mock-token", expiresIn = 3600)
        }

        assertEquals("real-token", real.token)
        assertEquals("mock-token", mock.token)
    }

    @Test
    fun `coalesces concurrent refreshes per token scope`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-02T00:00:00Z"))
        val cache = KisAccessTokenCache(KisTokenProperties(), clock)
        var fetchCount = 0

        val tokens = (1..10).map {
            async {
                cache.getOrRefresh(KisTokenScope.REAL) {
                    fetchCount += 1
                    tokenResponse("shared-token", expiresIn = 3600)
                }
            }
        }.awaitAll()

        assertEquals(List(10) { "shared-token" }, tokens.map { it.token })
        assertEquals(1, fetchCount)
    }

    @Test
    fun `rejects malformed token response`() = runTest {
        val cache = KisAccessTokenCache(KisTokenProperties(), MutableClock(Instant.parse("2026-06-02T00:00:00Z")))

        assertFailsWith<IllegalArgumentException> {
            cache.getOrRefresh(KisTokenScope.REAL) {
                objectMapper.readTree("""{"expires_in":3600}""")
            }
        }
    }

    @Test
    fun `parses explicit KIS token expiry timestamp`() {
        val clock = MutableClock(Instant.parse("2026-06-02T00:00:00Z"))
        val cached = tokenResponse(
            token = "token",
            expiresAt = "2026-06-02 09:30:00",
        ).toCachedToken(clock, KisTokenProperties())

        assertEquals(Instant.parse("2026-06-02T00:30:00Z"), cached.expiresAt)
    }

    private fun tokenResponse(
        token: String,
        expiresIn: Long? = null,
        expiresAt: String? = null,
    ) = objectMapper.createObjectNode().apply {
        put("access_token", token)
        expiresIn?.let { put("expires_in", it) }
        expiresAt?.let { put("access_token_token_expired", it) }
    }

    private class MutableClock(
        private var instant: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant

        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }
    }
}

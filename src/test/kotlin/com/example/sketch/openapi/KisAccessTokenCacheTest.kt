package com.example.sketch.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.nio.file.Path
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KisAccessTokenCacheTest {

    private val objectMapper = ObjectMapper()

    @TempDir
    lateinit var tempDir: Path

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
    fun `loads usable token from persistent store before fetching`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-02T00:00:00Z"))
        val store = FileKisAccessTokenStore(tempDir.resolve("kis-tokens.json"), objectMapper)
        store.save(
            KisTokenScope.REAL,
            CachedKisAccessToken(
                token = "persisted-token",
                expiresAt = Instant.parse("2026-06-02T01:00:00Z"),
            ),
        )
        val cache = KisAccessTokenCache(KisTokenProperties(), clock, store)
        var fetchCount = 0

        val token = cache.getOrRefresh(KisTokenScope.REAL) {
            fetchCount += 1
            tokenResponse("fresh-token", expiresIn = 3600)
        }

        assertEquals("persisted-token", token.token)
        assertEquals(0, fetchCount)
    }

    @Test
    fun `persists refreshed token for a new cache instance`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-02T00:00:00Z"))
        val store = FileKisAccessTokenStore(tempDir.resolve("kis-tokens.json"), objectMapper)
        val firstCache = KisAccessTokenCache(KisTokenProperties(), clock, store)
        val secondCache = KisAccessTokenCache(KisTokenProperties(), clock, store)
        var fetchCount = 0

        val first = firstCache.getOrRefresh(KisTokenScope.MOCK) {
            fetchCount += 1
            tokenResponse("persisted-mock-token", expiresIn = 3600)
        }
        val second = secondCache.getOrRefresh(KisTokenScope.MOCK) {
            fetchCount += 1
            tokenResponse("unexpected-token", expiresIn = 3600)
        }

        assertEquals("persisted-mock-token", first.token)
        assertEquals("persisted-mock-token", second.token)
        assertEquals(1, fetchCount)
    }

    @Test
    fun `reloads persistent store after acquiring refresh lock`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-02T00:00:00Z"))
        val store = FileKisAccessTokenStore(tempDir.resolve("kis-tokens.json"), objectMapper)
        val refreshLock = BeforeBlockRefreshLock {
            store.save(
                KisTokenScope.REAL,
                CachedKisAccessToken(
                    token = "refreshed-by-peer",
                    expiresAt = Instant.parse("2026-06-02T01:00:00Z"),
                ),
            )
        }
        val cache = KisAccessTokenCache(KisTokenProperties(), clock, store, refreshLock)
        var fetchCount = 0

        val token = cache.getOrRefresh(KisTokenScope.REAL) {
            fetchCount += 1
            tokenResponse("duplicate-refresh", expiresIn = 3600)
        }

        assertEquals("refreshed-by-peer", token.token)
        assertEquals(0, fetchCount)
        assertEquals(1, refreshLock.calls)
    }

    @Test
    fun `jdbc store saves loads and deletes scoped tokens`() {
        val store = JdbcKisAccessTokenStore(jdbcTemplate())
        val token = CachedKisAccessToken(
            token = "shared-token",
            expiresAt = Instant.parse("2026-06-02T01:00:00Z"),
        )

        store.save(KisTokenScope.REAL, token)

        assertEquals(token, store.load(KisTokenScope.REAL))
        assertNull(store.load(KisTokenScope.MOCK))

        store.delete(KisTokenScope.REAL)

        assertNull(store.load(KisTokenScope.REAL))
    }

    @Test
    fun `jdbc refresh lock releases scope after block`() = runBlocking {
        val jdbcTemplate = jdbcTemplate()
        val first = JdbcKisTokenRefreshLock(jdbcTemplate, lockProperties(), ownerId = "owner-1")
        val second = JdbcKisTokenRefreshLock(jdbcTemplate, lockProperties(), ownerId = "owner-2")
        var firstAcquired = false
        var secondAcquired = false

        first.withLock(KisTokenScope.REAL) {
            firstAcquired = true
        }
        second.withLock(KisTokenScope.REAL) {
            secondAcquired = true
        }

        assertTrue(firstAcquired)
        assertTrue(secondAcquired)
    }

    @Test
    fun `jdbc refresh lock times out while another owner holds scope`() = runBlocking {
        val jdbcTemplate = jdbcTemplate()
        val first = JdbcKisTokenRefreshLock(jdbcTemplate, lockProperties(), ownerId = "owner-1")
        val second = JdbcKisTokenRefreshLock(
            jdbcTemplate,
            lockProperties(waitTimeout = Duration.ofMillis(20), retryDelay = Duration.ofMillis(1)),
            ownerId = "owner-2",
        )
        var failure: IllegalStateException? = null

        first.withLock(KisTokenScope.REAL) {
            try {
                second.withLock(KisTokenScope.REAL) {
                    error("second owner must not acquire an active refresh lock")
                }
            } catch (ex: IllegalStateException) {
                failure = ex
            }
        }

        assertNotNull(failure)
        assertTrue(failure?.message?.contains("Timed out") == true)
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

    private fun jdbcTemplate(): JdbcTemplate {
        val dataSource = DriverManagerDataSource(
            "jdbc:h2:mem:kis_token_${System.nanoTime()};MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )
        return JdbcTemplate(dataSource).apply {
            execute(
                """
                CREATE TABLE kis_access_token (
                    token_scope VARCHAR(16) NOT NULL,
                    access_token TEXT NOT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (token_scope)
                )
                """.trimIndent(),
            )
            execute(
                """
                CREATE TABLE kis_token_refresh_lock (
                    token_scope VARCHAR(16) NOT NULL,
                    owner_id VARCHAR(128) NOT NULL,
                    locked_until TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (token_scope)
                )
                """.trimIndent(),
            )
        }
    }

    private fun lockProperties(
        waitTimeout: Duration = Duration.ofMillis(100),
        retryDelay: Duration = Duration.ofMillis(5),
    ): KisTokenProperties {
        return KisTokenProperties().apply {
            persistence.lockTtl = Duration.ofSeconds(30)
            persistence.lockWaitTimeout = waitTimeout
            persistence.lockRetryDelay = retryDelay
        }
    }

    private class BeforeBlockRefreshLock(
        private val beforeBlock: () -> Unit,
    ) : KisTokenRefreshLock {
        var calls: Int = 0
            private set

        override suspend fun <T> withLock(scope: KisTokenScope, block: suspend () -> T): T {
            calls += 1
            beforeBlock()
            return block()
        }
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

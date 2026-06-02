package com.example.sketch.openapi

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

enum class KisTokenScope {
    REAL,
    MOCK,
}

data class CachedKisAccessToken(
    val token: String,
    val expiresAt: Instant,
) {
    fun isUsable(now: Instant, properties: KisTokenProperties): Boolean {
        return token.isNotBlank() && now.plus(properties.refreshBeforeExpiry).isBefore(expiresAt)
    }
}

class KisAccessTokenCache(
    private val properties: KisTokenProperties,
    private val clock: Clock = Clock.systemUTC(),
    private val tokenStore: KisAccessTokenStore = NoopKisAccessTokenStore,
) {
    private val tokens = ConcurrentHashMap<KisTokenScope, CachedKisAccessToken>()
    private val locks = ConcurrentHashMap<KisTokenScope, Mutex>()

    suspend fun getOrRefresh(
        scope: KisTokenScope,
        fetch: suspend () -> JsonNode,
    ): TokenResponse {
        val now = clock.instant()
        tokens[scope]?.takeIf { it.isUsable(now, properties) }?.let {
            return TokenResponse(token = it.token)
        }

        return lockFor(scope).withLock {
            val lockedNow = clock.instant()
            tokens[scope]?.takeIf { it.isUsable(lockedNow, properties) }?.let {
                return@withLock TokenResponse(token = it.token)
            }
            tokenStore.load(scope)?.takeIf { it.isUsable(lockedNow, properties) }?.let {
                tokens[scope] = it
                return@withLock TokenResponse(token = it.token)
            }

            val cached = fetch().toCachedToken(clock, properties)
            tokens[scope] = cached
            tokenStore.save(scope, cached)
            TokenResponse(token = cached.token)
        }
    }

    fun invalidate(scope: KisTokenScope) {
        tokens.remove(scope)
        tokenStore.delete(scope)
    }

    private fun lockFor(scope: KisTokenScope): Mutex {
        return locks.computeIfAbsent(scope) { Mutex() }
    }
}

internal fun JsonNode.toCachedToken(
    clock: Clock,
    properties: KisTokenProperties,
): CachedKisAccessToken {
    val token = path("access_token").asText("").trim()
    require(token.isNotBlank()) { "KIS token response has no access_token" }
    val now = clock.instant()
    val expiresAt = parseExplicitTokenExpiry()
        ?: parseExpiresIn(now)
        ?: now.plus(properties.fallbackTtl)
    return CachedKisAccessToken(token = token, expiresAt = expiresAt)
}

private fun JsonNode.parseExplicitTokenExpiry(): Instant? {
    val value = path("access_token_token_expired").asText("").trim()
    if (value.isBlank()) return null
    return runCatching {
        LocalDateTime.parse(value, TOKEN_EXPIRY_FORMATTER)
            .atZone(KIS_TOKEN_EXPIRY_ZONE)
            .toInstant()
    }.getOrNull()
}

private fun JsonNode.parseExpiresIn(now: Instant): Instant? {
    val expiresIn = path("expires_in").asLong(0)
    return expiresIn.takeIf { it > 0 }?.let { now.plusSeconds(it) }
}

private val TOKEN_EXPIRY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val KIS_TOKEN_EXPIRY_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

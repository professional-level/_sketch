package com.example.sketch.openapi.toss.adapter.out.api

import com.example.sketch.openapi.toss.application.port.`in`.TossAccessTokenResult
import com.example.sketch.openapi.toss.domain.TossOpenApiTokenException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant

class TossAccessTokenCache(
    private val properties: TossOpenApiProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Mutex()
    @Volatile
    private var cachedToken: CachedTossAccessToken? = null

    suspend fun getOrRefresh(
        forceRefresh: Boolean = false,
        fetch: suspend () -> TossAccessTokenResult,
    ): TossAccessTokenResult {
        val now = clock.instant()
        if (!forceRefresh) {
            cachedToken?.takeIf { it.isUsable(now, properties) }?.let {
                return it.toResult()
            }
        }

        return lock.withLock {
            val lockedNow = clock.instant()
            if (!forceRefresh) {
                cachedToken?.takeIf { it.isUsable(lockedNow, properties) }?.let {
                    return@withLock it.toResult()
                }
            }

            val refreshedToken = fetch().toCachedToken(lockedNow, properties)
            cachedToken = refreshedToken
            refreshedToken.toResult()
        }
    }

    fun invalidate() {
        cachedToken = null
    }
}

data class CachedTossAccessToken(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: Instant,
    val expiresIn: Long?,
    val scope: String?,
    val rawBody: Any?,
) {
    fun isUsable(now: Instant, properties: TossOpenApiProperties): Boolean {
        return accessToken.isNotBlank() &&
            now.plus(properties.token.refreshBeforeExpiry.coercePositive()).isBefore(expiresAt)
    }

    fun toResult(): TossAccessTokenResult {
        return TossAccessTokenResult(
            accessToken = accessToken,
            tokenType = tokenType,
            expiresIn = expiresIn,
            expiresAt = expiresAt,
            scope = scope,
            rawBody = rawBody,
        )
    }
}

private fun TossAccessTokenResult.toCachedToken(
    now: Instant,
    properties: TossOpenApiProperties,
): CachedTossAccessToken {
    val token = accessToken.trim()
    if (token.isBlank()) {
        throw TossOpenApiTokenException("Toss token response has no access_token")
    }
    val ttl = expiresIn
        ?.takeIf { it > 0 }
        ?.let { java.time.Duration.ofSeconds(it) }
        ?: properties.token.fallbackTtl.coercePositive()
    return CachedTossAccessToken(
        accessToken = token,
        tokenType = tokenType.ifBlank { "Bearer" },
        expiresAt = now.plus(ttl),
        expiresIn = expiresIn,
        scope = scope,
        rawBody = rawBody,
    )
}

private fun java.time.Duration.coercePositive(): java.time.Duration {
    return if (isZero || isNegative) java.time.Duration.ofSeconds(1) else this
}

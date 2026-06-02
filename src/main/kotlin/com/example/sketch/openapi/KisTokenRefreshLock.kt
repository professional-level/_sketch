package com.example.sketch.openapi

import kotlinx.coroutines.delay
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

interface KisTokenRefreshLock {
    suspend fun <T> withLock(scope: KisTokenScope, block: suspend () -> T): T
}

object NoopKisTokenRefreshLock : KisTokenRefreshLock {
    override suspend fun <T> withLock(scope: KisTokenScope, block: suspend () -> T): T {
        return block()
    }
}

class JdbcKisTokenRefreshLock(
    private val jdbcTemplate: JdbcTemplate,
    private val properties: KisTokenProperties,
    private val clock: Clock = Clock.systemUTC(),
    private val ownerId: String = defaultOwnerId(),
) : KisTokenRefreshLock {

    override suspend fun <T> withLock(scope: KisTokenScope, block: suspend () -> T): T {
        acquire(scope)
        return try {
            block()
        } finally {
            release(scope)
        }
    }

    private suspend fun acquire(scope: KisTokenScope) {
        val deadline = clock.instant().plus(properties.persistence.lockWaitTimeout)
        while (!tryAcquire(scope)) {
            if (!clock.instant().isBefore(deadline)) {
                error("Timed out waiting for KIS token refresh lock for scope ${scope.name}")
            }
            delay(properties.persistence.lockRetryDelay.toMillis())
        }
    }

    private fun tryAcquire(scope: KisTokenScope): Boolean {
        val now = clock.instant()
        val lockedUntil = now.plus(properties.persistence.lockTtl)
        val updated = jdbcTemplate.update(
            """
            UPDATE kis_token_refresh_lock
            SET owner_id = ?, locked_until = ?, updated_at = CURRENT_TIMESTAMP
            WHERE token_scope = ?
              AND (locked_until <= ? OR owner_id = ?)
            """.trimIndent(),
            ownerId,
            Timestamp.from(lockedUntil),
            scope.name,
            Timestamp.from(now),
            ownerId,
        )
        if (updated > 0) {
            return true
        }

        return try {
            jdbcTemplate.update(
                """
                INSERT INTO kis_token_refresh_lock (token_scope, owner_id, locked_until, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """.trimIndent(),
                scope.name,
                ownerId,
                Timestamp.from(lockedUntil),
            ) > 0
        } catch (_: DuplicateKeyException) {
            false
        }
    }

    private fun release(scope: KisTokenScope) {
        jdbcTemplate.update(
            """
            DELETE FROM kis_token_refresh_lock
            WHERE token_scope = ?
              AND owner_id = ?
            """.trimIndent(),
            scope.name,
            ownerId,
        )
    }

    private companion object {
        fun defaultOwnerId(): String {
            return "${UUID.randomUUID()}"
        }
    }
}

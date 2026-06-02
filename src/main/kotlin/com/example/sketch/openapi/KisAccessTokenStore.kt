package com.example.sketch.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant
import java.sql.Timestamp
import org.springframework.jdbc.core.JdbcTemplate

interface KisAccessTokenStore {
    fun load(scope: KisTokenScope): CachedKisAccessToken?
    fun save(scope: KisTokenScope, token: CachedKisAccessToken)
    fun delete(scope: KisTokenScope)
}

object NoopKisAccessTokenStore : KisAccessTokenStore {
    override fun load(scope: KisTokenScope): CachedKisAccessToken? = null
    override fun save(scope: KisTokenScope, token: CachedKisAccessToken) = Unit
    override fun delete(scope: KisTokenScope) = Unit
}

class FileKisAccessTokenStore(
    private val path: Path,
    private val objectMapper: ObjectMapper,
) : KisAccessTokenStore {
    private val lock = Any()

    override fun load(scope: KisTokenScope): CachedKisAccessToken? = synchronized(lock) {
        readFile().tokenFor(scope)?.toCachedToken()
    }

    override fun save(scope: KisTokenScope, token: CachedKisAccessToken) = synchronized(lock) {
        val current = readFile()
        writeFile(current.withToken(scope, token.toStoredToken()))
    }

    override fun delete(scope: KisTokenScope) = synchronized(lock) {
        val current = readFile()
        writeFile(current.withToken(scope, null))
    }

    private fun readFile(): StoredKisTokenFile {
        if (!Files.exists(path)) {
            return StoredKisTokenFile()
        }
        return runCatching {
            objectMapper.readValue(path.toFile(), StoredKisTokenFile::class.java)
        }.getOrElse {
            StoredKisTokenFile()
        }
    }

    private fun writeFile(value: StoredKisTokenFile) {
        val parent = path.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val temp = Files.createTempFile(parent ?: Path.of("."), tempFilePrefix(), ".tmp")
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), value)
            moveIntoPlace(temp)
            restrictOwnerAccess(path)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun moveIntoPlace(temp: Path) {
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun restrictOwnerAccess(target: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                target,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                ),
            )
        }
    }

    private fun tempFilePrefix(): String {
        val prefix = path.fileName.toString()
        return if (prefix.length >= MIN_TEMP_FILE_PREFIX_LENGTH) prefix else "kis-token"
    }

    private companion object {
        const val MIN_TEMP_FILE_PREFIX_LENGTH = 3
    }
}

class JdbcKisAccessTokenStore(
    private val jdbcTemplate: JdbcTemplate,
) : KisAccessTokenStore {
    override fun load(scope: KisTokenScope): CachedKisAccessToken? {
        return jdbcTemplate.query(
            """
            SELECT access_token, expires_at
            FROM kis_access_token
            WHERE token_scope = ?
            """.trimIndent(),
            { rs, _ ->
                CachedKisAccessToken(
                    token = rs.getString("access_token"),
                    expiresAt = rs.getTimestamp("expires_at").toInstant(),
                )
            },
            scope.name,
        ).firstOrNull()
    }

    override fun save(scope: KisTokenScope, token: CachedKisAccessToken) {
        jdbcTemplate.update(
            """
            INSERT INTO kis_access_token (token_scope, access_token, expires_at, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                access_token = VALUES(access_token),
                expires_at = VALUES(expires_at),
                updated_at = CURRENT_TIMESTAMP
            """.trimIndent(),
            scope.name,
            token.token,
            Timestamp.from(token.expiresAt),
        )
    }

    override fun delete(scope: KisTokenScope) {
        jdbcTemplate.update(
            """
            DELETE FROM kis_access_token
            WHERE token_scope = ?
            """.trimIndent(),
            scope.name,
        )
    }
}

data class StoredKisTokenFile(
    val real: StoredKisToken? = null,
    val mock: StoredKisToken? = null,
) {
    fun tokenFor(scope: KisTokenScope): StoredKisToken? {
        return when (scope) {
            KisTokenScope.REAL -> real
            KisTokenScope.MOCK -> mock
        }
    }

    fun withToken(scope: KisTokenScope, token: StoredKisToken?): StoredKisTokenFile {
        return when (scope) {
            KisTokenScope.REAL -> copy(real = token)
            KisTokenScope.MOCK -> copy(mock = token)
        }
    }
}

data class StoredKisToken(
    val token: String = "",
    val expiresAt: String = "",
) {
    fun toCachedToken(): CachedKisAccessToken? {
        val parsedExpiresAt = runCatching { Instant.parse(expiresAt) }.getOrNull() ?: return null
        return CachedKisAccessToken(token = token, expiresAt = parsedExpiresAt)
    }
}

private fun CachedKisAccessToken.toStoredToken(): StoredKisToken {
    return StoredKisToken(
        token = token,
        expiresAt = expiresAt.toString(),
    )
}

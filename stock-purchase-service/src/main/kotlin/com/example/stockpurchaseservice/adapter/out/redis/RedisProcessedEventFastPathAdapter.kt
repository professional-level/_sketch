package com.example.stockpurchaseservice.adapter.out.redis

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.application.port.out.ProcessedEventFastPathDecision
import com.example.stockpurchaseservice.application.port.out.ProcessedEventFastPathPort
import com.example.stockpurchaseservice.config.redis.StockPurchaseRedisProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.script.RedisScript
import java.security.MessageDigest
import java.util.UUID

@PersistenceAdapter
@ConditionalOnProperty(
    prefix = "akra.redis.processed-event",
    name = ["enabled"],
    havingValue = "true",
)
internal class RedisProcessedEventFastPathAdapter(
    private val scriptRunner: RedisStringScriptRunner,
    private val properties: StockPurchaseRedisProperties,
) : ProcessedEventFastPathPort {

    override suspend fun tryAcquire(eventId: UUID, idempotencyKey: String): ProcessedEventFastPathDecision {
        return try {
            when (
                execute(
                    script = ACQUIRE_SCRIPT,
                    keys = listOf(eventKey(eventId), idempotencyKey(idempotencyKey)),
                    args = listOf(leaseTtlMs().toString(), eventId.toString()),
                )
            ) {
                "ACQUIRED" -> ProcessedEventFastPathDecision.ACQUIRED
                "DUPLICATE" -> ProcessedEventFastPathDecision.DUPLICATE
                else -> ProcessedEventFastPathDecision.UNAVAILABLE
            }
        } catch (exception: Throwable) {
            logger.warn("redis processed-event acquire unavailable: {}", exception.message)
            ProcessedEventFastPathDecision.UNAVAILABLE
        }
    }

    override suspend fun markCompleted(eventId: UUID) {
        runCatching {
            execute(
                script = MARK_COMPLETED_SCRIPT,
                keys = listOf(eventKey(eventId)),
                args = listOf(completedTtlMs().toString()),
            )
        }.onFailure { exception ->
            logger.warn("redis processed-event completion marker unavailable: {}", exception.message)
        }
    }

    override suspend fun release(eventId: UUID) {
        runCatching {
            execute(
                script = RELEASE_SCRIPT,
                keys = listOf(eventKey(eventId)),
                args = emptyList(),
            )
        }.onFailure { exception ->
            logger.warn("redis processed-event release unavailable: {}", exception.message)
        }
    }

    private fun execute(script: RedisScript<String>, keys: List<String>, args: List<String>): String? {
        return scriptRunner.run(script, keys, args)
    }

    private fun eventKey(eventId: UUID): String {
        return "$processedEventPrefix:event:$eventId"
    }

    private fun idempotencyKey(idempotencyKey: String): String {
        return "$processedEventPrefix:idempotency:${idempotencyKey.sha256()}"
    }

    private val processedEventPrefix: String
        get() = "${properties.keyPrefix.trimEnd(':')}:processed-event"

    private fun leaseTtlMs(): Long {
        return properties.processedEvent.leaseTtl.toMillis().coerceAtLeast(MIN_TTL_MS)
    }

    private fun completedTtlMs(): Long {
        return properties.processedEvent.completedTtl.toMillis().coerceAtLeast(MIN_TTL_MS)
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RedisProcessedEventFastPathAdapter::class.java)
        private const val MIN_TTL_MS = 1L
        private val ACQUIRE_SCRIPT = RedisScript.of(
            """
            local event_key = KEYS[1]
            local idempotency_key = KEYS[2]
            local lease_ttl_ms = ARGV[1]
            local event_id = ARGV[2]

            if redis.call('EXISTS', event_key) == 1 then
              return 'DUPLICATE'
            end
            if redis.call('EXISTS', idempotency_key) == 1 then
              return 'DUPLICATE'
            end

            redis.call('SET', event_key, idempotency_key, 'PX', lease_ttl_ms)
            redis.call('SET', idempotency_key, event_id, 'PX', lease_ttl_ms)
            return 'ACQUIRED'
            """.trimIndent(),
            String::class.java,
        )
        private val MARK_COMPLETED_SCRIPT = RedisScript.of(
            """
            local event_key = KEYS[1]
            local completed_ttl_ms = ARGV[1]
            local idempotency_key = redis.call('GET', event_key)

            redis.call('SET', event_key, 'SUCCESS', 'PX', completed_ttl_ms)
            if idempotency_key and idempotency_key ~= 'SUCCESS' then
              redis.call('SET', idempotency_key, 'SUCCESS', 'PX', completed_ttl_ms)
            end
            return 'OK'
            """.trimIndent(),
            String::class.java,
        )
        private val RELEASE_SCRIPT = RedisScript.of(
            """
            local event_key = KEYS[1]
            local idempotency_key = redis.call('GET', event_key)

            redis.call('DEL', event_key)
            if idempotency_key and idempotency_key ~= 'SUCCESS' then
              redis.call('DEL', idempotency_key)
            end
            return 'OK'
            """.trimIndent(),
            String::class.java,
        )
    }
}

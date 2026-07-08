package com.example.stockpurchaseservice.adapter.out.redis

import com.example.stockpurchaseservice.application.port.out.ProcessedEventFastPathDecision
import com.example.stockpurchaseservice.config.redis.StockPurchaseRedisProperties
import kotlinx.coroutines.runBlocking
import org.springframework.data.redis.core.script.RedisScript
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class RedisProcessedEventFastPathAdapterTest {

    @Test
    fun `acquires event lease using event and idempotency keys`() = runBlocking {
        val runner = FakeRedisStringScriptRunner("ACQUIRED")
        val adapter = RedisProcessedEventFastPathAdapter(runner, properties())
        val eventId = UUID.fromString("11111111-1111-1111-1111-111111111111")

        val result = adapter.tryAcquire(eventId, "strategy:event:1")

        assertEquals(ProcessedEventFastPathDecision.ACQUIRED, result)
        assertEquals(
            RedisCall(
                keys = listOf(
                    "akra:test:processed-event:event:$eventId",
                    "akra:test:processed-event:idempotency:${"strategy:event:1".sha256()}",
                ),
                args = listOf("300000", eventId.toString()),
            ),
            runner.calls.single(),
        )
    }

    @Test
    fun `maps duplicate and unknown acquire results`() = runBlocking {
        val runner = FakeRedisStringScriptRunner("DUPLICATE", "unexpected")
        val adapter = RedisProcessedEventFastPathAdapter(runner, properties())

        val duplicate = adapter.tryAcquire(UUID.randomUUID(), "same-event")
        val unavailable = adapter.tryAcquire(UUID.randomUUID(), "unknown-event")

        assertEquals(ProcessedEventFastPathDecision.DUPLICATE, duplicate)
        assertEquals(ProcessedEventFastPathDecision.UNAVAILABLE, unavailable)
    }

    @Test
    fun `returns unavailable when redis acquire fails`() = runBlocking {
        val adapter = RedisProcessedEventFastPathAdapter(
            FakeRedisStringScriptRunner(failure = IllegalStateException("redis down")),
            properties(),
        )

        val result = adapter.tryAcquire(UUID.randomUUID(), "strategy:event:1")

        assertEquals(ProcessedEventFastPathDecision.UNAVAILABLE, result)
    }

    @Test
    fun `marks completed with completed ttl`() = runBlocking {
        val runner = FakeRedisStringScriptRunner("OK")
        val adapter = RedisProcessedEventFastPathAdapter(runner, properties())
        val eventId = UUID.fromString("22222222-2222-2222-2222-222222222222")

        adapter.markCompleted(eventId)

        assertEquals(
            RedisCall(
                keys = listOf("akra:test:processed-event:event:$eventId"),
                args = listOf("604800000"),
            ),
            runner.calls.single(),
        )
    }

    @Test
    fun `releases event lease without ttl args`() = runBlocking {
        val runner = FakeRedisStringScriptRunner("OK")
        val adapter = RedisProcessedEventFastPathAdapter(runner, properties())
        val eventId = UUID.fromString("33333333-3333-3333-3333-333333333333")

        adapter.release(eventId)

        assertEquals(
            RedisCall(
                keys = listOf("akra:test:processed-event:event:$eventId"),
                args = emptyList(),
            ),
            runner.calls.single(),
        )
    }

    private fun properties(): StockPurchaseRedisProperties {
        return StockPurchaseRedisProperties().apply {
            keyPrefix = "akra:test:"
            processedEvent.leaseTtl = Duration.ofMinutes(5)
            processedEvent.completedTtl = Duration.ofDays(7)
        }
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

internal data class RedisCall(
    val keys: List<String>,
    val args: List<String>,
)

internal class FakeRedisStringScriptRunner(
    vararg results: String?,
    private val failure: Throwable? = null,
) : RedisStringScriptRunner {
    val calls: MutableList<RedisCall> = mutableListOf()
    private val results: ArrayDeque<String?> = ArrayDeque(results.toList())

    override fun run(script: RedisScript<String>, keys: List<String>, args: List<String>): String? {
        failure?.let { throw it }
        calls += RedisCall(keys = keys, args = args)
        return results.removeFirstOrNull()
    }
}

package com.example.stockpurchaseservice.adapter.out.idempotency

import com.example.stockpurchaseservice.application.port.out.ProcessedEventFastPathDecision
import com.example.stockpurchaseservice.application.port.out.ProcessedEventFastPathPort
import com.example.stockpurchaseservice.application.port.out.ProcessedEventPort
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisBackedProcessedEventAdapterTest {

    @Test
    fun `returns duplicate before db when fast path already has event`() = runBlocking {
        val delegate = FakeProcessedEventPort()
        val fastPath = FakeProcessedEventFastPathPort(
            acquireDecisions = ArrayDeque(listOf(ProcessedEventFastPathDecision.DUPLICATE)),
        )
        val adapter = RedisBackedProcessedEventAdapter(delegate, fastPath)

        val started = adapter.tryStart(UUID.randomUUID(), "strategy:event:1")

        assertFalse(started)
        assertEquals(emptyList(), delegate.tryStartCalls)
    }

    @Test
    fun `falls back to db when fast path is unavailable`() = runBlocking {
        val delegate = FakeProcessedEventPort(tryStartResult = true)
        val fastPath = FakeProcessedEventFastPathPort(
            acquireDecisions = ArrayDeque(listOf(ProcessedEventFastPathDecision.UNAVAILABLE)),
        )
        val adapter = RedisBackedProcessedEventAdapter(delegate, fastPath)
        val eventId = UUID.randomUUID()

        val started = adapter.tryStart(eventId, "strategy:event:1")

        assertTrue(started)
        assertEquals(listOf(eventId to "strategy:event:1"), delegate.tryStartCalls)
    }

    @Test
    fun `marks completed when db reports duplicate after fast path acquire`() = runBlocking {
        val delegate = FakeProcessedEventPort(tryStartResult = false)
        val fastPath = FakeProcessedEventFastPathPort(
            acquireDecisions = ArrayDeque(listOf(ProcessedEventFastPathDecision.ACQUIRED)),
        )
        val adapter = RedisBackedProcessedEventAdapter(delegate, fastPath)
        val eventId = UUID.randomUUID()

        val started = adapter.tryStart(eventId, "strategy:event:1")

        assertFalse(started)
        assertEquals(listOf(eventId), fastPath.completedEvents)
    }

    @Test
    fun `releases fast path lease when db start fails`() = runBlocking {
        val delegate = FakeProcessedEventPort(tryStartFailure = IllegalStateException("db down"))
        val fastPath = FakeProcessedEventFastPathPort(
            acquireDecisions = ArrayDeque(listOf(ProcessedEventFastPathDecision.ACQUIRED)),
        )
        val adapter = RedisBackedProcessedEventAdapter(delegate, fastPath)
        val eventId = UUID.randomUUID()

        assertFailsWith<IllegalStateException> {
            adapter.tryStart(eventId, "strategy:event:1")
        }
        assertEquals(listOf(eventId), fastPath.releasedEvents)
    }

    @Test
    fun `marks fast path completed after db success`() = runBlocking {
        val delegate = FakeProcessedEventPort()
        val fastPath = FakeProcessedEventFastPathPort()
        val adapter = RedisBackedProcessedEventAdapter(delegate, fastPath)
        val eventId = UUID.randomUUID()

        adapter.markSuccess(eventId)

        assertEquals(listOf(eventId), delegate.successEvents)
        assertEquals(listOf(eventId), fastPath.completedEvents)
    }

    @Test
    fun `releases fast path after db failure marker`() = runBlocking {
        val delegate = FakeProcessedEventPort()
        val fastPath = FakeProcessedEventFastPathPort()
        val adapter = RedisBackedProcessedEventAdapter(delegate, fastPath)
        val eventId = UUID.randomUUID()

        adapter.markFailed(eventId, "broker timeout")

        assertEquals(listOf<Pair<UUID, String?>>(eventId to "broker timeout"), delegate.failedEvents)
        assertEquals(listOf(eventId), fastPath.releasedEvents)
    }

    private class FakeProcessedEventPort(
        private val tryStartResult: Boolean = true,
        private val tryStartFailure: Throwable? = null,
    ) : ProcessedEventPort {
        val tryStartCalls: MutableList<Pair<UUID, String>> = mutableListOf()
        val successEvents: MutableList<UUID> = mutableListOf()
        val failedEvents: MutableList<Pair<UUID, String?>> = mutableListOf()

        override suspend fun tryStart(eventId: UUID, idempotencyKey: String): Boolean {
            tryStartCalls += eventId to idempotencyKey
            tryStartFailure?.let { throw it }
            return tryStartResult
        }

        override suspend fun markSuccess(eventId: UUID) {
            successEvents += eventId
        }

        override suspend fun markFailed(eventId: UUID, reason: String?) {
            failedEvents += eventId to reason
        }
    }

    private class FakeProcessedEventFastPathPort(
        private val acquireDecisions: ArrayDeque<ProcessedEventFastPathDecision> = ArrayDeque(),
    ) : ProcessedEventFastPathPort {
        val completedEvents: MutableList<UUID> = mutableListOf()
        val releasedEvents: MutableList<UUID> = mutableListOf()

        override suspend fun tryAcquire(eventId: UUID, idempotencyKey: String): ProcessedEventFastPathDecision {
            return acquireDecisions.removeFirstOrNull() ?: ProcessedEventFastPathDecision.ACQUIRED
        }

        override suspend fun markCompleted(eventId: UUID) {
            completedEvents += eventId
        }

        override suspend fun release(eventId: UUID) {
            releasedEvents += eventId
        }
    }
}

package com.example.stockpurchaseservice.application.port.out

import java.util.UUID

interface ProcessedEventFastPathPort {
    suspend fun tryAcquire(eventId: UUID, idempotencyKey: String): ProcessedEventFastPathDecision
    suspend fun markCompleted(eventId: UUID)
    suspend fun release(eventId: UUID)
}

enum class ProcessedEventFastPathDecision {
    ACQUIRED,
    DUPLICATE,
    UNAVAILABLE,
}

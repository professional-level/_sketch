package com.example.stockpurchaseservice.application.port.out

import java.util.UUID

interface ProcessedEventPort {
    suspend fun tryStart(eventId: UUID, idempotencyKey: String): Boolean
    suspend fun markSuccess(eventId: UUID)
    suspend fun markFailed(eventId: UUID, reason: String?)
}

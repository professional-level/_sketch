package com.example.stockpurchaseservice.application.port.out

import common.MessageTopic
import java.time.ZonedDateTime
import java.util.UUID

interface OrderExecutionOutboxPort {
    suspend fun findUnpublished(limit: Int): List<OrderExecutionOutboxMessage>
    suspend fun markPublished(id: UUID)
    suspend fun markFailed(id: UUID, reason: String?, nextAttemptAt: ZonedDateTime)
}

data class OrderExecutionOutboxMessage(
    val id: UUID,
    val topic: MessageTopic,
    val messageKey: String,
    val payload: ByteArray,
    val retryCount: Int,
    val traceId: String? = null,
    val spanId: String? = null,
    val traceParent: String? = null,
)

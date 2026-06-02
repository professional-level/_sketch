package com.example.stockpurchaseservice.application.port.out

import common.MessageTopic
import java.time.ZonedDateTime
import java.util.UUID

interface OrderExecutionOutboxPort {
    suspend fun claimPublishable(limit: Int, claimOwner: String, claimExpiresAt: ZonedDateTime): List<OrderExecutionOutboxMessage>
    suspend fun markPublished(id: UUID, claimOwner: String): Boolean
    suspend fun markFailed(id: UUID, claimOwner: String, reason: String?, nextAttemptAt: ZonedDateTime): Boolean
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

package com.example.strategyexecutionservice.application.port.out

import common.MessageTopic
import java.time.ZonedDateTime
import java.util.UUID

interface OrderIntentOutboxPort {
    suspend fun claimPublishable(limit: Int, claimOwner: String, claimExpiresAt: ZonedDateTime): List<OrderIntentOutboxMessage>
    suspend fun markPublished(id: UUID, claimOwner: String)
    suspend fun markFailed(id: UUID, claimOwner: String, reason: String?, nextAttemptAt: ZonedDateTime)
}

data class OrderIntentOutboxMessage(
    val id: UUID,
    val topic: MessageTopic,
    val messageKey: String,
    val payload: ByteArray,
    val retryCount: Int,
    val traceId: String? = null,
    val spanId: String? = null,
    val traceParent: String? = null,
)

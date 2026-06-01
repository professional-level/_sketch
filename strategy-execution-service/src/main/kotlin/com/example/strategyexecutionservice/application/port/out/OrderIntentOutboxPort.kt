package com.example.strategyexecutionservice.application.port.out

import common.MessageTopic
import java.util.UUID

interface OrderIntentOutboxPort {
    suspend fun findUnpublished(limit: Int): List<OrderIntentOutboxMessage>
    suspend fun markPublished(id: UUID)
    suspend fun markFailed(id: UUID, reason: String?)
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

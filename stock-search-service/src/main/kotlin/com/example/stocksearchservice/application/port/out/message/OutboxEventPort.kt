package com.example.stocksearchservice.application.port.out.message

import com.example.common.application.event.ApplicationEvent
import com.example.common.application.event.EventMessage
import common.MessageTopic
import java.util.UUID

interface OutboxEventPort {
    suspend fun saveAll(messages: List<EventMessage<ApplicationEvent>>)
    suspend fun findUnpublished(limit: Int): List<OutboxEventDto>
    suspend fun markPublished(id: UUID)
    suspend fun markFailed(id: UUID, reason: String?)
}

data class OutboxEventDto(
    val id: UUID,
    val topic: MessageTopic,
    val payload: ByteArray,
    val retryCount: Int,
)

package com.example.stocksearchservice.adapter.out.persistence.entity

import common.MessageTopic
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(name = "outbox_event")
internal class OutboxEvent private constructor(
    @Id
    @Column(nullable = false)
    val id: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val topic: MessageTopic,
    @Column(nullable = false)
    val eventType: String,
    @Lob
    @Column(nullable = false)
    val payload: ByteArray,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OutboxEventStatus,
    @Column(nullable = false)
    var retryCount: Int,
    @Column(nullable = false)
    val createdAt: ZonedDateTime,
    @Column
    var publishedAt: ZonedDateTime?,
    @Column
    var failureReason: String?,
) {
    fun published() {
        status = OutboxEventStatus.PUBLISHED
        publishedAt = ZonedDateTime.now()
        failureReason = null
    }

    fun failed(reason: String?) {
        status = OutboxEventStatus.FAILED
        retryCount += 1
        failureReason = reason
    }

    companion object {
        fun pending(
            id: UUID,
            topic: MessageTopic,
            eventType: String,
            payload: ByteArray,
        ): OutboxEvent {
            return OutboxEvent(
                id = id,
                topic = topic,
                eventType = eventType,
                payload = payload,
                status = OutboxEventStatus.PENDING,
                retryCount = 0,
                createdAt = ZonedDateTime.now(),
                publishedAt = null,
                failureReason = null,
            )
        }
    }
}

internal enum class OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED,
}

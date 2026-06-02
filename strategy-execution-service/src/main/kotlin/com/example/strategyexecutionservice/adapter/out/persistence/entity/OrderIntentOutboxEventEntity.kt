package com.example.strategyexecutionservice.adapter.out.persistence.entity

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
@Table(name = "order_intent_outbox_event")
internal class OrderIntentOutboxEventEntity private constructor(
    @Id
    @Column(nullable = false)
    val id: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val topic: MessageTopic,
    @Column(nullable = false)
    val messageKey: String,
    @Column(nullable = false)
    val eventType: String,
    @Lob
    @Column(nullable = false)
    val payload: ByteArray,
    @Column(name = "traceId")
    val traceId: String?,
    @Column(name = "spanId")
    val spanId: String?,
    @Column(name = "traceParent")
    val traceParent: String?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderIntentOutboxEventStatus,
    @Column(nullable = false)
    var retryCount: Int,
    @Column(nullable = false)
    val createdAt: ZonedDateTime,
    @Column
    var publishedAt: ZonedDateTime?,
    @Column
    var failureReason: String?,
    @Column(name = "nextAttemptAt")
    var nextAttemptAt: ZonedDateTime?,
    @Column(name = "claimOwner")
    var claimOwner: String?,
    @Column(name = "claimExpiresAt")
    var claimExpiresAt: ZonedDateTime?,
) {
    fun claimed(owner: String, expiresAt: ZonedDateTime) {
        status = OrderIntentOutboxEventStatus.PROCESSING
        claimOwner = owner
        claimExpiresAt = expiresAt
    }

    fun published() {
        status = OrderIntentOutboxEventStatus.PUBLISHED
        publishedAt = ZonedDateTime.now()
        failureReason = null
        nextAttemptAt = null
        claimOwner = null
        claimExpiresAt = null
    }

    fun failed(reason: String?, nextAttemptAt: ZonedDateTime) {
        status = OrderIntentOutboxEventStatus.FAILED
        retryCount += 1
        failureReason = reason
        this.nextAttemptAt = nextAttemptAt
        claimOwner = null
        claimExpiresAt = null
    }

    companion object {
        fun pending(
            id: UUID,
            topic: MessageTopic,
            messageKey: String,
            eventType: String,
            payload: ByteArray,
            traceId: String? = null,
            spanId: String? = null,
            traceParent: String? = null,
        ): OrderIntentOutboxEventEntity {
            return OrderIntentOutboxEventEntity(
                id = id,
                topic = topic,
                messageKey = messageKey,
                eventType = eventType,
                payload = payload,
                traceId = traceId,
                spanId = spanId,
                traceParent = traceParent,
                status = OrderIntentOutboxEventStatus.PENDING,
                retryCount = 0,
                createdAt = ZonedDateTime.now(),
                publishedAt = null,
                failureReason = null,
                nextAttemptAt = null,
                claimOwner = null,
                claimExpiresAt = null,
            )
        }
    }
}

internal enum class OrderIntentOutboxEventStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED,
}

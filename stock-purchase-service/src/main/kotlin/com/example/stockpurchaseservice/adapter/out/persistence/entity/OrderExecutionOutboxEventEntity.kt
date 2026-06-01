package com.example.stockpurchaseservice.adapter.out.persistence.entity

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
@Table(name = "order_execution_outbox_event")
internal class OrderExecutionOutboxEventEntity private constructor(
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
    @Column
    val traceId: String?,
    @Column
    val spanId: String?,
    @Column
    val traceParent: String?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderExecutionOutboxEventStatus,
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
        status = OrderExecutionOutboxEventStatus.PUBLISHED
        publishedAt = ZonedDateTime.now()
        failureReason = null
    }

    fun failed(reason: String?) {
        status = OrderExecutionOutboxEventStatus.FAILED
        retryCount += 1
        failureReason = reason
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
        ): OrderExecutionOutboxEventEntity {
            return OrderExecutionOutboxEventEntity(
                id = id,
                topic = topic,
                messageKey = messageKey,
                eventType = eventType,
                payload = payload,
                traceId = traceId,
                spanId = spanId,
                traceParent = traceParent,
                status = OrderExecutionOutboxEventStatus.PENDING,
                retryCount = 0,
                createdAt = ZonedDateTime.now(),
                publishedAt = null,
                failureReason = null,
            )
        }
    }
}

internal enum class OrderExecutionOutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED,
}

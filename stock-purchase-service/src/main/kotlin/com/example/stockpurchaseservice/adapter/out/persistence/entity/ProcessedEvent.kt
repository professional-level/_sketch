package com.example.stockpurchaseservice.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(name = "processed_event")
internal class ProcessedEvent private constructor(
    @Id
    @Column(nullable = false)
    val eventId: UUID,
    @Column(nullable = false, unique = true)
    val idempotencyKey: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProcessedEventStatus,
    @Column(nullable = false)
    val startedAt: ZonedDateTime,
    @Column
    var completedAt: ZonedDateTime?,
    @Column
    var failureReason: String?,
) {
    fun success() {
        status = ProcessedEventStatus.SUCCESS
        completedAt = ZonedDateTime.now()
        failureReason = null
    }

    fun failed(reason: String?) {
        status = ProcessedEventStatus.FAILED
        completedAt = ZonedDateTime.now()
        failureReason = reason
    }

    companion object {
        fun started(eventId: UUID, idempotencyKey: String): ProcessedEvent {
            return ProcessedEvent(
                eventId = eventId,
                idempotencyKey = idempotencyKey,
                status = ProcessedEventStatus.PROCESSING,
                startedAt = ZonedDateTime.now(),
                completedAt = null,
                failureReason = null,
            )
        }
    }
}

internal enum class ProcessedEventStatus {
    PROCESSING,
    SUCCESS,
    FAILED,
}

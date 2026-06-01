package com.example.stockpurchaseservice.adapter.out.persistence.entity

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(
    name = "order_intent_submission",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_order_intent_submission_idempotency", columnNames = ["idempotencyKey"]),
        UniqueConstraint(name = "uk_order_intent_submission_external_order", columnNames = ["externalOrderId"]),
    ],
)
internal class OrderIntentSubmissionEntity private constructor(
    @Id
    @Column(nullable = false)
    val orderIntentId: UUID,
    @Column(nullable = false)
    val idempotencyKey: String,
    @Column(nullable = false)
    val strategyExecutionId: String,
    @Column(nullable = false)
    val symbol: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val side: OrderIntentSubmissionSide,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val orderType: OrderIntentSubmissionType,
    @Column
    val submittedPrice: Double?,
    @Column(nullable = false)
    val quantity: Long,
    @Column(nullable = false)
    val orderTag: String,
    @Column(nullable = false)
    val internalOrderId: UUID,
    @Column
    val externalOrderId: String?,
    @Column(nullable = false)
    val submittedAt: ZonedDateTime,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: OrderIntentSubmissionStatus,
    @Column(length = 1000)
    val statusReason: String?,
    @Column
    val lastStatusCheckedAt: ZonedDateTime?,
) {
    fun toDto(): OrderIntentSubmissionDto {
        return OrderIntentSubmissionDto(
            orderIntentId = orderIntentId,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = strategyExecutionId,
            symbol = symbol,
            side = side.toDto(),
            orderType = orderType.toDto(),
            submittedPrice = submittedPrice,
            quantity = quantity,
            orderTag = orderTag,
            internalOrderId = internalOrderId,
            externalOrderId = externalOrderId,
            submittedAt = submittedAt,
            status = status.toDto(),
            statusReason = statusReason,
            lastStatusCheckedAt = lastStatusCheckedAt,
        )
    }

    companion object {
        fun from(dto: OrderIntentSubmissionDto): OrderIntentSubmissionEntity {
            return OrderIntentSubmissionEntity(
                orderIntentId = dto.orderIntentId,
                idempotencyKey = dto.idempotencyKey,
                strategyExecutionId = dto.strategyExecutionId,
                symbol = dto.symbol,
                side = OrderIntentSubmissionSide.from(dto.side),
                orderType = OrderIntentSubmissionType.from(dto.orderType),
                submittedPrice = dto.submittedPrice,
                quantity = dto.quantity,
                orderTag = dto.orderTag,
                internalOrderId = dto.internalOrderId,
                externalOrderId = dto.externalOrderId,
                submittedAt = dto.submittedAt,
                status = OrderIntentSubmissionStatus.from(dto.status),
                statusReason = dto.statusReason?.take(1000),
                lastStatusCheckedAt = dto.lastStatusCheckedAt,
            )
        }
    }
}

internal enum class OrderIntentSubmissionStatus {
    SUBMITTED,
    SUBMISSION_UNKNOWN,
    REJECTED,
    CANCELLED,
    ;

    fun toDto(): OrderIntentSubmissionStatusDto {
        return when (this) {
            SUBMITTED -> OrderIntentSubmissionStatusDto.SUBMITTED
            SUBMISSION_UNKNOWN -> OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN
            REJECTED -> OrderIntentSubmissionStatusDto.REJECTED
            CANCELLED -> OrderIntentSubmissionStatusDto.CANCELLED
        }
    }

    companion object {
        fun from(dto: OrderIntentSubmissionStatusDto): OrderIntentSubmissionStatus {
            return when (dto) {
                OrderIntentSubmissionStatusDto.SUBMITTED -> SUBMITTED
                OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN -> SUBMISSION_UNKNOWN
                OrderIntentSubmissionStatusDto.REJECTED -> REJECTED
                OrderIntentSubmissionStatusDto.CANCELLED -> CANCELLED
            }
        }
    }
}

internal enum class OrderIntentSubmissionSide {
    BUY,
    SELL,
    ;

    fun toDto(): OrderIntentSide {
        return when (this) {
            BUY -> OrderIntentSide.BUY
            SELL -> OrderIntentSide.SELL
        }
    }

    companion object {
        fun from(side: OrderIntentSide): OrderIntentSubmissionSide {
            return when (side) {
                OrderIntentSide.BUY -> BUY
                OrderIntentSide.SELL -> SELL
            }
        }
    }
}

internal enum class OrderIntentSubmissionType {
    LOC,
    MOC,
    LIMIT,
    ;

    fun toDto(): OrderIntentType {
        return when (this) {
            LOC -> OrderIntentType.LOC
            MOC -> OrderIntentType.MOC
            LIMIT -> OrderIntentType.LIMIT
        }
    }

    companion object {
        fun from(type: OrderIntentType): OrderIntentSubmissionType {
            return when (type) {
                OrderIntentType.LOC -> LOC
                OrderIntentType.MOC -> MOC
                OrderIntentType.LIMIT -> LIMIT
            }
        }
    }
}

package com.example.strategyexecutionservice.adapter.out.persistence.entity

import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventRecord
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventType
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

@Entity
@Table(
    name = "strategy_execution_order_event",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_strategy_order_event_idempotency", columnNames = ["idempotencyKey"]),
    ],
)
internal class StrategyExecutionOrderEventEntity private constructor(
    @Id
    @Column(nullable = false)
    val eventId: String,
    @Column(nullable = false)
    val idempotencyKey: String,
    @Column(nullable = false)
    val strategyExecutionId: String,
    @Column(nullable = false)
    val orderIntentId: String,
    @Column
    val brokerOrderId: String?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: StrategyExecutionOrderEventEntityType,
    @Enumerated(EnumType.STRING)
    @Column
    val side: StrategyExecutionOrderEventSide?,
    @Column
    val price: Double?,
    @Column
    val quantity: Long?,
    @Column
    val orderTag: String?,
    @Column
    val reason: String?,
    @Column(nullable = false)
    val occurredAt: ZonedDateTime,
) {
    fun toDto(): StrategyExecutionOrderEventRecord {
        return StrategyExecutionOrderEventRecord(
            eventId = eventId,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = strategyExecutionId,
            orderIntentId = orderIntentId,
            brokerOrderId = brokerOrderId,
            type = type.toDto(),
            side = side?.toDto(),
            price = price,
            quantity = quantity,
            orderTag = orderTag,
            reason = reason,
            occurredAt = occurredAt,
        )
    }

    companion object {
        fun from(dto: StrategyExecutionOrderEventRecord): StrategyExecutionOrderEventEntity {
            return StrategyExecutionOrderEventEntity(
                eventId = dto.eventId,
                idempotencyKey = dto.idempotencyKey,
                strategyExecutionId = dto.strategyExecutionId,
                orderIntentId = dto.orderIntentId,
                brokerOrderId = dto.brokerOrderId,
                type = StrategyExecutionOrderEventEntityType.from(dto.type),
                side = dto.side?.let(StrategyExecutionOrderEventSide::from),
                price = dto.price,
                quantity = dto.quantity,
                orderTag = dto.orderTag,
                reason = dto.reason,
                occurredAt = dto.occurredAt,
            )
        }
    }
}

internal enum class StrategyExecutionOrderEventEntityType {
    SUBMITTED,
    REJECTED,
    CANCELLED,
    FILLED,
    PARTIALLY_FILLED,
    ;

    fun toDto(): StrategyExecutionOrderEventType {
        return when (this) {
            SUBMITTED -> StrategyExecutionOrderEventType.SUBMITTED
            REJECTED -> StrategyExecutionOrderEventType.REJECTED
            CANCELLED -> StrategyExecutionOrderEventType.CANCELLED
            FILLED -> StrategyExecutionOrderEventType.FILLED
            PARTIALLY_FILLED -> StrategyExecutionOrderEventType.PARTIALLY_FILLED
        }
    }

    companion object {
        fun from(type: StrategyExecutionOrderEventType): StrategyExecutionOrderEventEntityType {
            return when (type) {
                StrategyExecutionOrderEventType.SUBMITTED -> SUBMITTED
                StrategyExecutionOrderEventType.REJECTED -> REJECTED
                StrategyExecutionOrderEventType.CANCELLED -> CANCELLED
                StrategyExecutionOrderEventType.FILLED -> FILLED
                StrategyExecutionOrderEventType.PARTIALLY_FILLED -> PARTIALLY_FILLED
            }
        }
    }
}

internal enum class StrategyExecutionOrderEventSide {
    BUY,
    SELL,
    ;

    fun toDto(): OrderSide {
        return when (this) {
            BUY -> OrderSide.BUY
            SELL -> OrderSide.SELL
        }
    }

    companion object {
        fun from(side: OrderSide): StrategyExecutionOrderEventSide {
            return when (side) {
                OrderSide.BUY -> BUY
                OrderSide.SELL -> SELL
            }
        }
    }
}

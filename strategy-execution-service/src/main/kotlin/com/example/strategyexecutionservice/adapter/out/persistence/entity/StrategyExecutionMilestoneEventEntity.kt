package com.example.strategyexecutionservice.adapter.out.persistence.entity

import com.example.strategyexecutionservice.application.port.out.StrategyExecutionMilestoneEventRecord
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

@Entity
@Table(
    name = "strategy_execution_milestone_event",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_strategy_milestone_idempotency", columnNames = ["idempotencyKey"]),
    ],
    indexes = [
        Index(name = "idx_strategy_milestone_execution_time", columnList = "strategyExecutionId, occurredAt"),
        Index(name = "idx_strategy_milestone_order_intent", columnList = "orderIntentId"),
        Index(name = "idx_strategy_milestone_type_time", columnList = "milestoneType, occurredAt"),
    ],
)
internal class StrategyExecutionMilestoneEventEntity private constructor(
    @Id
    @Column(nullable = false)
    val eventId: String,
    @Column(nullable = false)
    val milestoneType: String,
    @Column(nullable = false)
    val strategyExecutionId: String,
    @Column(nullable = false)
    val orderIntentId: String,
    @Column
    val brokerOrderId: String?,
    @Column
    val orderTag: String?,
    @Enumerated(EnumType.STRING)
    @Column
    val side: StrategyExecutionMilestoneEventSide?,
    @Column(nullable = false)
    val filledQuantity: Long,
    @Column
    val averageFilledPrice: Double?,
    @Column(nullable = false)
    val occurredAt: ZonedDateTime,
    @Column(nullable = false)
    val sourceEventIds: String,
    @Column(nullable = false)
    val idempotencyKey: String,
) {
    fun toDto(): StrategyExecutionMilestoneEventRecord {
        return StrategyExecutionMilestoneEventRecord(
            eventId = eventId,
            milestoneType = milestoneType,
            strategyExecutionId = strategyExecutionId,
            orderIntentId = orderIntentId,
            brokerOrderId = brokerOrderId,
            orderTag = orderTag,
            side = side?.toDto(),
            filledQuantity = filledQuantity,
            averageFilledPrice = averageFilledPrice,
            occurredAt = occurredAt,
            sourceEventIds = sourceEventIds.split(SOURCE_EVENT_ID_SEPARATOR).filter(String::isNotBlank),
            idempotencyKey = idempotencyKey,
        )
    }

    companion object {
        fun from(dto: StrategyExecutionMilestoneEventRecord): StrategyExecutionMilestoneEventEntity {
            return StrategyExecutionMilestoneEventEntity(
                eventId = dto.eventId,
                milestoneType = dto.milestoneType,
                strategyExecutionId = dto.strategyExecutionId,
                orderIntentId = dto.orderIntentId,
                brokerOrderId = dto.brokerOrderId,
                orderTag = dto.orderTag,
                side = dto.side?.let(StrategyExecutionMilestoneEventSide::from),
                filledQuantity = dto.filledQuantity,
                averageFilledPrice = dto.averageFilledPrice,
                occurredAt = dto.occurredAt,
                sourceEventIds = dto.sourceEventIds.joinToString(SOURCE_EVENT_ID_SEPARATOR),
                idempotencyKey = dto.idempotencyKey,
            )
        }
    }
}

internal enum class StrategyExecutionMilestoneEventSide {
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
        fun from(side: OrderSide): StrategyExecutionMilestoneEventSide {
            return when (side) {
                OrderSide.BUY -> BUY
                OrderSide.SELL -> SELL
            }
        }
    }
}

private const val SOURCE_EVENT_ID_SEPARATOR = "\n"

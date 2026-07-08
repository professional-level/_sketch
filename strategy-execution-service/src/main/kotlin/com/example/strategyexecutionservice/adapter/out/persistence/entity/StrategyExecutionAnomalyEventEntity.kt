package com.example.strategyexecutionservice.adapter.out.persistence.entity

import com.example.strategyexecutionservice.application.port.out.StrategyExecutionAnomalyEventRecord
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
    name = "strategy_execution_anomaly_event",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_strategy_anomaly_idempotency", columnNames = ["idempotencyKey"]),
    ],
)
internal class StrategyExecutionAnomalyEventEntity private constructor(
    @Id
    @Column(nullable = false)
    val eventId: String,
    @Column(nullable = false)
    val idempotencyKey: String,
    @Column(nullable = false)
    val anomalyType: String,
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
    val side: StrategyExecutionAnomalyEventSide?,
    @Column(nullable = false, length = 2048)
    val reason: String,
    @Column(nullable = false)
    val occurredAt: ZonedDateTime,
    @Column(nullable = false, length = 2048)
    val sourceEventIds: String,
) {
    fun toDto(): StrategyExecutionAnomalyEventRecord {
        return StrategyExecutionAnomalyEventRecord(
            eventId = eventId,
            anomalyType = anomalyType,
            strategyExecutionId = strategyExecutionId,
            orderIntentId = orderIntentId,
            brokerOrderId = brokerOrderId,
            orderTag = orderTag,
            side = side?.toDto(),
            reason = reason,
            occurredAt = occurredAt,
            sourceEventIds = sourceEventIds.lines().filter(String::isNotBlank),
            idempotencyKey = idempotencyKey,
        )
    }

    companion object {
        fun from(dto: StrategyExecutionAnomalyEventRecord): StrategyExecutionAnomalyEventEntity {
            return StrategyExecutionAnomalyEventEntity(
                eventId = dto.eventId,
                idempotencyKey = dto.idempotencyKey,
                anomalyType = dto.anomalyType,
                strategyExecutionId = dto.strategyExecutionId,
                orderIntentId = dto.orderIntentId,
                brokerOrderId = dto.brokerOrderId,
                orderTag = dto.orderTag,
                side = dto.side?.let(StrategyExecutionAnomalyEventSide::from),
                reason = dto.reason,
                occurredAt = dto.occurredAt,
                sourceEventIds = dto.sourceEventIds.joinToString("\n"),
            )
        }
    }
}

internal enum class StrategyExecutionAnomalyEventSide {
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
        fun from(side: OrderSide): StrategyExecutionAnomalyEventSide {
            return when (side) {
                OrderSide.BUY -> BUY
                OrderSide.SELL -> SELL
            }
        }
    }
}

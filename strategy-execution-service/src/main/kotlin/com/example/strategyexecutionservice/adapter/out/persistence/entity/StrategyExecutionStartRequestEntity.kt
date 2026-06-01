package com.example.strategyexecutionservice.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "strategy_execution_start_request")
internal class StrategyExecutionStartRequestEntity private constructor(
    @Id
    @Column(nullable = false)
    val idempotencyKey: String,
    @Column(nullable = false)
    val processedAt: ZonedDateTime,
) {
    companion object {
        fun processed(idempotencyKey: String, processedAt: ZonedDateTime = ZonedDateTime.now()): StrategyExecutionStartRequestEntity {
            require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
            return StrategyExecutionStartRequestEntity(
                idempotencyKey = idempotencyKey,
                processedAt = processedAt,
            )
        }
    }
}

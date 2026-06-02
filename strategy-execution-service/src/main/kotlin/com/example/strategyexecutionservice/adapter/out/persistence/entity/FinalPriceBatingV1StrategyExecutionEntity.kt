package com.example.strategyexecutionservice.adapter.out.persistence.entity

import com.example.strategyexecutionservice.application.port.out.FinalPriceBatingV1ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "final_price_bating_v1_strategy_execution")
internal class FinalPriceBatingV1StrategyExecutionEntity private constructor(
    @Id
    @Column(nullable = false)
    val executionId: String,
    @Column(nullable = false)
    val symbol: String,
    @Column(nullable = false)
    val market: String,
    @Column(nullable = false)
    val budget: Double,
    @Column(nullable = false)
    val targetBuyPrice: Double,
    @Column(nullable = false)
    val quantity: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: FinalPriceBatingV1StrategyExecutionStatus,
    @Column(nullable = false)
    val filledQuantity: Long,
    @Column
    val averageFilledPrice: Double?,
    @Column
    val sellTargetPrice: Double?,
    @Column(nullable = false)
    val sellQuantity: Long,
    @Column
    val sellIntentCreatedAt: ZonedDateTime?,
    @Column(nullable = false)
    val soldQuantity: Long,
    @Column
    val averageSoldPrice: Double?,
    @Column(nullable = false)
    val startedAt: ZonedDateTime,
    @Column
    val completedAt: ZonedDateTime?,
) {
    fun toDto(): FinalPriceBatingV1ExecutionState {
        return FinalPriceBatingV1ExecutionState(
            executionId = executionId,
            symbol = symbol,
            market = market,
            budget = budget,
            targetBuyPrice = targetBuyPrice,
            quantity = quantity,
            status = status.toDto(),
            filledQuantity = filledQuantity,
            averageFilledPrice = averageFilledPrice,
            sellTargetPrice = sellTargetPrice,
            sellQuantity = sellQuantity,
            sellIntentCreatedAt = sellIntentCreatedAt,
            soldQuantity = soldQuantity,
            averageSoldPrice = averageSoldPrice,
            startedAt = startedAt,
            completedAt = completedAt,
        )
    }

    companion object {
        fun from(dto: FinalPriceBatingV1ExecutionState): FinalPriceBatingV1StrategyExecutionEntity {
            return FinalPriceBatingV1StrategyExecutionEntity(
                executionId = dto.executionId,
                symbol = dto.symbol,
                market = dto.market,
                budget = dto.budget,
                targetBuyPrice = dto.targetBuyPrice,
                quantity = dto.quantity,
                status = FinalPriceBatingV1StrategyExecutionStatus.from(dto.status),
                filledQuantity = dto.filledQuantity,
                averageFilledPrice = dto.averageFilledPrice,
                sellTargetPrice = dto.sellTargetPrice,
                sellQuantity = dto.sellQuantity,
                sellIntentCreatedAt = dto.sellIntentCreatedAt,
                soldQuantity = dto.soldQuantity,
                averageSoldPrice = dto.averageSoldPrice,
                startedAt = dto.startedAt,
                completedAt = dto.completedAt,
            )
        }
    }
}

internal enum class FinalPriceBatingV1StrategyExecutionStatus {
    ACTIVE,
    COMPLETED,
    ;

    fun toDto(): StrategyExecutionLifecycleStatus {
        return when (this) {
            ACTIVE -> StrategyExecutionLifecycleStatus.ACTIVE
            COMPLETED -> StrategyExecutionLifecycleStatus.COMPLETED
        }
    }

    companion object {
        fun from(status: StrategyExecutionLifecycleStatus): FinalPriceBatingV1StrategyExecutionStatus {
            return when (status) {
                StrategyExecutionLifecycleStatus.ACTIVE -> ACTIVE
                StrategyExecutionLifecycleStatus.COMPLETED -> COMPLETED
            }
        }
    }
}

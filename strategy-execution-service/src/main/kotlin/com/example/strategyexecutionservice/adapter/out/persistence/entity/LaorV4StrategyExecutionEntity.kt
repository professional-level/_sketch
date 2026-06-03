package com.example.strategyexecutionservice.adapter.out.persistence.entity

import com.example.strategyexecutionservice.application.port.out.LaorV4ExecutionState
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionLifecycleStatus
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyMode
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyState
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "laor_v4_strategy_execution")
internal class LaorV4StrategyExecutionEntity private constructor(
    @Id
    @Column(nullable = false)
    val executionId: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val symbol: LaorV4StrategyExecutionSymbol,
    @Column(nullable = false)
    val totalSplitCount: Int,
    @Column(nullable = false)
    val firstBuyLimitPercentAbovePreviousClose: Double,
    @Column(nullable = false)
    val autoRestart: Boolean,
    @Column(nullable = false)
    val cycleNo: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: LaorV4StrategyExecutionStatus,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val mode: LaorV4StrategyExecutionMode,
    @Column(nullable = false)
    val progressRound: Double,
    @Column(nullable = false)
    val availableCash: Double,
    @Column(nullable = false)
    val holdingQuantity: Long,
    @Column(nullable = false)
    val averagePurchasePrice: Double,
    @Column(nullable = false)
    val realizedProfitLoss: Double,
    @Column(nullable = false)
    val reverseModeElapsedDays: Int,
    @Column
    val lastExecutionRunId: String?,
    @Column
    val lastExecutedAt: ZonedDateTime?,
) {
    fun toDto(): LaorV4ExecutionState {
        return LaorV4ExecutionState(
            executionId = executionId,
            symbol = symbol.toDto(),
            totalSplitCount = totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = firstBuyLimitPercentAbovePreviousClose,
            autoRestart = autoRestart,
            cycleNo = cycleNo,
            status = status.toDto(),
            state = LaorV4StrategyState(
                mode = mode.toDto(),
                progressRound = progressRound,
                availableCash = availableCash,
                holdingQuantity = holdingQuantity,
                averagePurchasePrice = averagePurchasePrice,
                realizedProfitLoss = realizedProfitLoss,
                reverseModeElapsedDays = reverseModeElapsedDays,
            ),
            lastExecutionRunId = lastExecutionRunId,
            lastExecutedAt = lastExecutedAt,
        )
    }

    companion object {
        fun from(dto: LaorV4ExecutionState): LaorV4StrategyExecutionEntity {
            return LaorV4StrategyExecutionEntity(
                executionId = dto.executionId,
                symbol = LaorV4StrategyExecutionSymbol.from(dto.symbol),
                totalSplitCount = dto.totalSplitCount,
                firstBuyLimitPercentAbovePreviousClose = dto.firstBuyLimitPercentAbovePreviousClose,
                autoRestart = dto.autoRestart,
                cycleNo = dto.cycleNo,
                status = LaorV4StrategyExecutionStatus.from(dto.status),
                mode = LaorV4StrategyExecutionMode.from(dto.state.mode),
                progressRound = dto.state.progressRound,
                availableCash = dto.state.availableCash,
                holdingQuantity = dto.state.holdingQuantity,
                averagePurchasePrice = dto.state.averagePurchasePrice,
                realizedProfitLoss = dto.state.realizedProfitLoss,
                reverseModeElapsedDays = dto.state.reverseModeElapsedDays,
                lastExecutionRunId = dto.lastExecutionRunId,
                lastExecutedAt = dto.lastExecutedAt,
            )
        }
    }
}

internal enum class LaorV4StrategyExecutionSymbol {
    TQQQ,
    SOXL,
    ;

    fun toDto(): LaorV4StrategySymbol {
        return when (this) {
            TQQQ -> LaorV4StrategySymbol.TQQQ
            SOXL -> LaorV4StrategySymbol.SOXL
        }
    }

    companion object {
        fun from(symbol: LaorV4StrategySymbol): LaorV4StrategyExecutionSymbol {
            return when (symbol) {
                LaorV4StrategySymbol.TQQQ -> TQQQ
                LaorV4StrategySymbol.SOXL -> SOXL
            }
        }
    }
}

internal enum class LaorV4StrategyExecutionStatus {
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
        fun from(status: StrategyExecutionLifecycleStatus): LaorV4StrategyExecutionStatus {
            return when (status) {
                StrategyExecutionLifecycleStatus.ACTIVE -> ACTIVE
                StrategyExecutionLifecycleStatus.COMPLETED -> COMPLETED
            }
        }
    }
}

internal enum class LaorV4StrategyExecutionMode {
    NORMAL,
    REVERSE,
    ;

    fun toDto(): LaorV4StrategyMode {
        return when (this) {
            NORMAL -> LaorV4StrategyMode.NORMAL
            REVERSE -> LaorV4StrategyMode.REVERSE
        }
    }

    companion object {
        fun from(mode: LaorV4StrategyMode): LaorV4StrategyExecutionMode {
            return when (mode) {
                LaorV4StrategyMode.NORMAL -> NORMAL
                LaorV4StrategyMode.REVERSE -> REVERSE
            }
        }
    }
}

package com.example.stockpurchaseservice.adapter.out.persistence.entity

import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionDto
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "unmatched_execution")
internal class UnmatchedExecutionEntity private constructor(
    @Id
    @Column(nullable = false)
    val externalExecutionId: String,
    @Column(nullable = false)
    val externalOrderId: String,
    @Column(nullable = false)
    val stockId: String,
    @Column(nullable = false)
    val stockName: String,
    @Column(nullable = false)
    val createdAt: ZonedDateTime,
    @Column(nullable = false)
    val quantity: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: UnmatchedExecutionType,
    @Column(nullable = false, length = 500)
    val reason: String,
    @Column(nullable = false)
    val observedAt: ZonedDateTime,
) {
    companion object {
        fun from(dto: UnmatchedExecutionDto): UnmatchedExecutionEntity {
            return UnmatchedExecutionEntity(
                externalExecutionId = dto.externalExecutionId,
                externalOrderId = dto.externalOrderId,
                stockId = dto.stockId,
                stockName = dto.stockName,
                createdAt = dto.createdAt,
                quantity = dto.quantity,
                type = UnmatchedExecutionType.from(dto.type),
                reason = dto.reason.take(500),
                observedAt = dto.observedAt,
            )
        }
    }
}

internal enum class UnmatchedExecutionType {
    SELLING,
    PURCHASE,
    ;

    companion object {
        fun from(dto: ExecutionTypeDto): UnmatchedExecutionType {
            return when (dto) {
                ExecutionTypeDto.SELLING -> SELLING
                ExecutionTypeDto.PURCHASE -> PURCHASE
            }
        }
    }
}

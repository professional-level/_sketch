package com.example.stockpurchaseservice.adapter.out.persistence.entity

import com.example.stockpurchaseservice.application.port.out.ExecutionFillDto
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "execution_fill")
internal class ExecutionFillEntity private constructor(
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
    val type: ExecutionFillType,
) {
    companion object {
        fun from(dto: ExecutionFillDto): ExecutionFillEntity {
            return ExecutionFillEntity(
                externalExecutionId = dto.externalExecutionId,
                externalOrderId = dto.externalOrderId,
                stockId = dto.stockId,
                stockName = dto.stockName,
                createdAt = dto.createdAt,
                quantity = dto.quantity,
                type = ExecutionFillType.from(dto.type),
            )
        }
    }
}

internal enum class ExecutionFillType {
    SELLING,
    PURCHASE,
    ;

    companion object {
        fun from(dto: ExecutionTypeDto): ExecutionFillType {
            return when (dto) {
                ExecutionTypeDto.SELLING -> SELLING
                ExecutionTypeDto.PURCHASE -> PURCHASE
            }
        }
    }
}

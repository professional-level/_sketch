package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.domain.ExecutionFill
import com.example.stockpurchaseservice.domain.ExecutionType
import java.time.ZonedDateTime

interface ExecutionFillPort {
    suspend fun saveIfNew(fill: ExecutionFillDto): Boolean
}

data class ExecutionFillDto(
    val externalExecutionId: String,
    val externalOrderId: String,
    val stockId: String,
    val stockName: String,
    val createdAt: ZonedDateTime,
    val quantity: Int,
    val type: ExecutionTypeDto,
) {
    companion object {
        fun from(fill: ExecutionFill): ExecutionFillDto {
            return ExecutionFillDto(
                externalExecutionId = fill.externalExecutionId.value,
                externalOrderId = fill.externalOrderId.value,
                stockId = fill.stock.id.value,
                stockName = fill.stock.name,
                createdAt = fill.createdAt,
                quantity = fill.quantity,
                type = ExecutionTypeDto.from(fill.type),
            )
        }
    }
}

enum class ExecutionTypeDto {
    SELLING,
    PURCHASE,
    ;

    fun toDomain(): ExecutionType {
        return when (this) {
            SELLING -> ExecutionType.Selling
            PURCHASE -> ExecutionType.Purchase
        }
    }

    companion object {
        fun from(type: ExecutionType): ExecutionTypeDto {
            return when (type) {
                ExecutionType.Selling -> SELLING
                ExecutionType.Purchase -> PURCHASE
            }
        }
    }
}

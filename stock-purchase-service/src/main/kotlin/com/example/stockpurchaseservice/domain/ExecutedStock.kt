package com.example.stockpurchaseservice.domain

import java.time.ZonedDateTime

data class ExecutedStock(
    val stock: Stock,
    val createdAt: ZonedDateTime,
    val quantity: Int,
    val type: ExecutionType,
    val externalOrderId: ExternalOrderId,
    val externalExecutionId: ExternalExecutionId = ExternalExecutionId(
        "${externalOrderId.value}:${type.name}:${createdAt.toInstant()}:$quantity",
    ),
    // 매수가격, 익절 목표 가격, 손절 목표 가격 명시 필요
)

enum class ExecutionType {
    Selling,
    Purchase
}

data class ExecutionFill(
    val externalExecutionId: ExternalExecutionId,
    val externalOrderId: ExternalOrderId,
    val stock: Stock,
    val createdAt: ZonedDateTime,
    val quantity: Int,
    val type: ExecutionType,
) {
    companion object {
        fun from(executedStock: ExecutedStock): ExecutionFill {
            return ExecutionFill(
                externalExecutionId = executedStock.externalExecutionId,
                externalOrderId = executedStock.externalOrderId,
                stock = executedStock.stock,
                createdAt = executedStock.createdAt,
                quantity = executedStock.quantity,
                type = executedStock.type,
            )
        }
    }
}

@JvmInline
value class ExternalExecutionId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

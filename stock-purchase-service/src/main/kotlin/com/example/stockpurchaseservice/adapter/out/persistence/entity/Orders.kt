package com.example.com.example.stockpurchaseservice.adapter.out.persistence.entity

import com.example.com.example.stockpurchaseservice.application.repository.OrderDto
import com.example.com.example.stockpurchaseservice.application.repository.StrategyTypeDto
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(name = "stock_order")
internal class Order private constructor(
    @Id
    @Column(nullable = false)
    val id: UUID,
    @Column(nullable = false)
    val stockId: String,
    @Column(nullable = false)
    val stockName: String,
    @Column(nullable = false)
    val requestedAt: ZonedDateTime,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val strategyType: StrategyType,
    @Column
    val purchasedAt: ZonedDateTime?,
    @Column
    val sellingAt: ZonedDateTime?,
    @Column
    val purchasePrice: Double?,
    @Column
    val sellingPrice: Double?,
    @Column(nullable = false)
    val quantity: Int,
) {
    fun toDTO(): OrderDto = OrderDto(
        id = id,
        stockId = stockId,
        stockName = stockName,
        requestedAt = requestedAt,
        strategyType = strategyType.toDto(),
        purchasedAt = purchasedAt,
        sellingAt = sellingAt,
        purchasePrice = purchasePrice,
        sellingPrice = sellingPrice,
        quantity = quantity,
    )

    companion object {
        fun from(dto: OrderDto): Order {
            return Order(
                id = dto.id,
                stockId = dto.stockId,
                stockName = dto.stockName,
                requestedAt = dto.requestedAt,
                strategyType = StrategyType.fromDto(dto.strategyType),
                purchasedAt = dto.purchasedAt,
                sellingAt = dto.sellingAt,
                purchasePrice = dto.purchasePrice,
                sellingPrice = dto.sellingPrice,
                quantity = dto.quantity,
            )
        }
    }
}

enum class StrategyType {
    FINAL_PRICE_BATING_V1,
    ;

    fun toDto(): StrategyTypeDto {
        return when (this) {
            FINAL_PRICE_BATING_V1 -> StrategyTypeDto.FINAL_PRICE_BATING_V1
        }
    }

    companion object {
        fun fromDto(dto: StrategyTypeDto): StrategyType {
            return when (dto) {
                StrategyTypeDto.FINAL_PRICE_BATING_V1 -> FINAL_PRICE_BATING_V1
            }
        }
    }
}

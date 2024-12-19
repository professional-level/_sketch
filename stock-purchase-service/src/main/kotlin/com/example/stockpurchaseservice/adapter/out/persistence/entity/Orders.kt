package com.example.com.example.stockpurchaseservice.adapter.out.persistence.entity

import com.example.com.example.stockpurchaseservice.application.repository.OrderDto
import com.example.com.example.stockpurchaseservice.application.repository.OrderStateDto
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
internal class Orders private constructor(
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
    val orderState: OrderState,
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
        orderState = orderState.toApplicationDto(),
    )

    companion object {
        fun from(dto: OrderDto): Orders {
            return Orders(
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
                orderState = OrderState.from(dto.orderState)
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

enum class OrderState {
    PURCHASE_WAITING,  // 주문이 들어가기 전
    PURCHASE_IN_PROCESS, // 주문이 들어간 상태
    PURCHASE_COMPLETED, // 체결이 된 상태
    SELLING_WAITING,  // 주문이 들어가기 전
    SELLING_IN_PROCESS, // 주문이 들어간 상태
    SELLING_COMPLETED, // 체결이 된 상태
    ;

    fun toApplicationDto(): OrderStateDto {
        return when (this) {
            OrderState.PURCHASE_WAITING -> OrderStateDto.PURCHASE_WAITING
            OrderState.PURCHASE_IN_PROCESS -> OrderStateDto.PURCHASE_IN_PROCESS
            OrderState.PURCHASE_COMPLETED -> OrderStateDto.PURCHASE_COMPLETED
            OrderState.SELLING_WAITING -> OrderStateDto.SELLING_WAITING
            OrderState.SELLING_IN_PROCESS -> OrderStateDto.SELLING_IN_PROCESS
            OrderState.SELLING_COMPLETED -> OrderStateDto.SELLING_COMPLETED
        }
    }

    companion object {
        fun from(orderState: OrderStateDto): OrderState {
            return when (orderState) {
                OrderStateDto.PURCHASE_WAITING -> OrderState.PURCHASE_WAITING
                OrderStateDto.PURCHASE_IN_PROCESS -> OrderState.PURCHASE_IN_PROCESS
                OrderStateDto.PURCHASE_COMPLETED -> OrderState.PURCHASE_COMPLETED
                OrderStateDto.SELLING_WAITING -> OrderState.SELLING_WAITING
                OrderStateDto.SELLING_IN_PROCESS -> OrderState.SELLING_IN_PROCESS
                OrderStateDto.SELLING_COMPLETED -> OrderState.SELLING_COMPLETED
            }
        }
    }
}
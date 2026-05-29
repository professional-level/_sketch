package com.example.stockpurchaseservice.adapter.out.persistence.entity

import com.example.stockpurchaseservice.application.repository.OrderDto
import com.example.stockpurchaseservice.application.repository.OrderStateDto
import com.example.stockpurchaseservice.application.repository.StrategyTypeDto
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(
    name = "stock_order",
    uniqueConstraints = [UniqueConstraint(name = "uk_stock_order_strategy_id", columnNames = ["strategyId"])],
)
internal class Orders private constructor(
    @Id
    @Column(nullable = false)
    val id: UUID,
    @Column(unique = true)
    val strategyId: String?,
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
        strategyId = strategyId,
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
                strategyId = dto.strategyId,
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
    SUBMIT_FAILED,
    SUBMISSION_UNKNOWN,
    SELLING_WAITING,  // 주문이 들어가기 전
    SELLING_IN_PROCESS, // 주문이 들어간 상태
    SELLING_COMPLETED, // 체결이 된 상태
    ;

    fun toApplicationDto(): OrderStateDto {
        return when (this) {
            OrderState.PURCHASE_WAITING -> OrderStateDto.PURCHASE_WAITING
            OrderState.PURCHASE_IN_PROCESS -> OrderStateDto.PURCHASE_IN_PROCESS
            OrderState.PURCHASE_COMPLETED -> OrderStateDto.PURCHASE_COMPLETED
            OrderState.SUBMIT_FAILED -> OrderStateDto.SUBMIT_FAILED
            OrderState.SUBMISSION_UNKNOWN -> OrderStateDto.SUBMISSION_UNKNOWN
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
                OrderStateDto.SUBMIT_FAILED -> OrderState.SUBMIT_FAILED
                OrderStateDto.SUBMISSION_UNKNOWN -> OrderState.SUBMISSION_UNKNOWN
                OrderStateDto.SELLING_WAITING -> OrderState.SELLING_WAITING
                OrderStateDto.SELLING_IN_PROCESS -> OrderState.SELLING_IN_PROCESS
                OrderStateDto.SELLING_COMPLETED -> OrderState.SELLING_COMPLETED
            }
        }
    }
}

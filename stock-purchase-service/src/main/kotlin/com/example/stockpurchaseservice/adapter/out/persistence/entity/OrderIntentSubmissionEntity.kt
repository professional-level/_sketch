package com.example.stockpurchaseservice.adapter.out.persistence.entity

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderTradingEnvironment
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
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
    name = "order_intent_submission",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_order_intent_submission_idempotency", columnNames = ["idempotencyKey"]),
        UniqueConstraint(name = "uk_order_intent_submission_external_order", columnNames = ["externalOrderId"]),
    ],
)
internal class OrderIntentSubmissionEntity private constructor(
    @Id
    @Column(nullable = false)
    val orderIntentId: UUID,
    @Column(nullable = false)
    val idempotencyKey: String,
    @Column(nullable = false)
    val strategyExecutionId: String,
    @Column(nullable = false)
    val symbol: String,
    @Enumerated(EnumType.STRING)
    @Column
    val market: OrderIntentSubmissionMarket?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val side: OrderIntentSubmissionSide,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val orderType: OrderIntentSubmissionType,
    @Column
    val submittedPrice: Double?,
    @Column(nullable = false)
    val quantity: Long,
    @Column(nullable = false)
    val orderTag: String,
    @Column(nullable = false)
    val internalOrderId: UUID,
    @Column
    val externalOrderId: String?,
    @Column
    val branchOrderNumber: String?,
    @Column(nullable = false)
    val submittedAt: ZonedDateTime,
    @Enumerated(EnumType.STRING)
    @Column
    val tradingEnvironment: OrderIntentTradingEnvironment?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: OrderIntentSubmissionStatus,
    @Column(length = 1000)
    val statusReason: String?,
    @Column
    val lastStatusCheckedAt: ZonedDateTime?,
) {
    fun toDto(): OrderIntentSubmissionDto {
        return OrderIntentSubmissionDto(
            orderIntentId = orderIntentId,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = strategyExecutionId,
            symbol = symbol,
            market = market?.toDto(),
            side = side.toDto(),
            orderType = orderType.toDto(),
            submittedPrice = submittedPrice,
            quantity = quantity,
            orderTag = orderTag,
            internalOrderId = internalOrderId,
            externalOrderId = externalOrderId,
            branchOrderNumber = branchOrderNumber,
            submittedAt = submittedAt,
            tradingEnvironment = tradingEnvironment?.toDto(),
            status = status.toDto(),
            statusReason = statusReason,
            lastStatusCheckedAt = lastStatusCheckedAt,
        )
    }

    companion object {
        fun from(dto: OrderIntentSubmissionDto): OrderIntentSubmissionEntity {
            return OrderIntentSubmissionEntity(
                orderIntentId = dto.orderIntentId,
                idempotencyKey = dto.idempotencyKey,
                strategyExecutionId = dto.strategyExecutionId,
                symbol = dto.symbol,
                market = dto.market?.let(OrderIntentSubmissionMarket::from),
                side = OrderIntentSubmissionSide.from(dto.side),
                orderType = OrderIntentSubmissionType.from(dto.orderType),
                submittedPrice = dto.submittedPrice,
                quantity = dto.quantity,
                orderTag = dto.orderTag,
                internalOrderId = dto.internalOrderId,
                externalOrderId = dto.externalOrderId,
                branchOrderNumber = dto.branchOrderNumber,
                submittedAt = dto.submittedAt,
                tradingEnvironment = dto.tradingEnvironment?.let(OrderIntentTradingEnvironment::from),
                status = OrderIntentSubmissionStatus.from(dto.status),
                statusReason = dto.statusReason?.take(1000),
                lastStatusCheckedAt = dto.lastStatusCheckedAt,
            )
        }
    }
}

internal enum class OrderIntentSubmissionMarket {
    DOMESTIC,
    OVERSEAS_US,
    ;

    fun toDto(): StockOrderMarket {
        return when (this) {
            DOMESTIC -> StockOrderMarket.DOMESTIC
            OVERSEAS_US -> StockOrderMarket.OVERSEAS_US
        }
    }

    companion object {
        fun from(market: StockOrderMarket): OrderIntentSubmissionMarket {
            return when (market) {
                StockOrderMarket.DOMESTIC -> DOMESTIC
                StockOrderMarket.OVERSEAS_US -> OVERSEAS_US
            }
        }
    }
}

internal enum class OrderIntentTradingEnvironment {
    MOCK,
    LIVE,
    ;

    fun toDto(): OrderTradingEnvironment {
        return when (this) {
            MOCK -> OrderTradingEnvironment.MOCK
            LIVE -> OrderTradingEnvironment.LIVE
        }
    }

    companion object {
        fun from(environment: OrderTradingEnvironment): OrderIntentTradingEnvironment {
            return when (environment) {
                OrderTradingEnvironment.MOCK -> MOCK
                OrderTradingEnvironment.LIVE -> LIVE
            }
        }
    }
}

internal enum class OrderIntentSubmissionStatus {
    SUBMITTED,
    SUBMISSION_UNKNOWN,
    REJECTED,
    CANCELLED,
    CANCEL_PENDING,
    ;

    fun toDto(): OrderIntentSubmissionStatusDto {
        return when (this) {
            SUBMITTED -> OrderIntentSubmissionStatusDto.SUBMITTED
            SUBMISSION_UNKNOWN -> OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN
            REJECTED -> OrderIntentSubmissionStatusDto.REJECTED
            CANCELLED -> OrderIntentSubmissionStatusDto.CANCELLED
            CANCEL_PENDING -> OrderIntentSubmissionStatusDto.CANCEL_PENDING
        }
    }

    companion object {
        fun from(dto: OrderIntentSubmissionStatusDto): OrderIntentSubmissionStatus {
            return when (dto) {
                OrderIntentSubmissionStatusDto.SUBMITTED -> SUBMITTED
                OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN -> SUBMISSION_UNKNOWN
                OrderIntentSubmissionStatusDto.REJECTED -> REJECTED
                OrderIntentSubmissionStatusDto.CANCELLED -> CANCELLED
                OrderIntentSubmissionStatusDto.CANCEL_PENDING -> CANCEL_PENDING
            }
        }
    }
}

internal enum class OrderIntentSubmissionSide {
    BUY,
    SELL,
    ;

    fun toDto(): OrderIntentSide {
        return when (this) {
            BUY -> OrderIntentSide.BUY
            SELL -> OrderIntentSide.SELL
        }
    }

    companion object {
        fun from(side: OrderIntentSide): OrderIntentSubmissionSide {
            return when (side) {
                OrderIntentSide.BUY -> BUY
                OrderIntentSide.SELL -> SELL
            }
        }
    }
}

internal enum class OrderIntentSubmissionType {
    LOC,
    MOC,
    LIMIT,
    ;

    fun toDto(): OrderIntentType {
        return when (this) {
            LOC -> OrderIntentType.LOC
            MOC -> OrderIntentType.MOC
            LIMIT -> OrderIntentType.LIMIT
        }
    }

    companion object {
        fun from(type: OrderIntentType): OrderIntentSubmissionType {
            return when (type) {
                OrderIntentType.LOC -> LOC
                OrderIntentType.MOC -> MOC
                OrderIntentType.LIMIT -> LIMIT
            }
        }
    }
}

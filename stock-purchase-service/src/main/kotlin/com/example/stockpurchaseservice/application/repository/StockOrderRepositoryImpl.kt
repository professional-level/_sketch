package com.example.com.example.stockpurchaseservice.application.repository

import com.example.com.example.stockpurchaseservice.application.port.out.StockOrderPort
import com.example.com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.OrderId
import com.example.stockpurchaseservice.domain.StrategyType
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID

@Component
internal class StockOrderRepositoryImpl(
    private val stockOrderPort: StockOrderPort, // TODO: 이름 체크
) : StockOrderRepository {
    override fun findById(id: OrderId): Order? {
        TODO("Not yet implemented")
    }

    // TODO: save 구현은 왜 DomainRepo에 없을까? 구현필요
    override suspend fun save(order: Order) {
        stockOrderPort.save(order.toDto())
    }

    override suspend fun findAllNotCompleted(): List<Order> {
        TODO("Not yet implemented")
    }
}

private fun Order.toDto(): OrderDto {
    return OrderDto(
        id = this.id.value,
        stockId = this.stockId.value,
        requestedAt = this.requestedAt,
        strategyType = StrategyTypeDto.from(this.strategyType),
        purchasedAt = this.purchasedAt,
        sellingAt = this.sellingAt,
        purchasePrice = this.purchasePrice?.price,
        sellingPrice = this.sellingPrice?.price,
    )
}

data class OrderDto(
    val id: UUID,
    val stockId: String,
    val requestedAt: ZonedDateTime,
    val strategyType: StrategyTypeDto,
    val purchasedAt: ZonedDateTime?,
    val sellingAt: ZonedDateTime?,
    val purchasePrice: Double?,
    val sellingPrice: Double?,
)

// TODO: common module로 이동 필요.
enum class StrategyTypeDto {
    FINAL_PRICE_BATING_V1,
    // 다른 타입 추가 가능
    ;

    companion object {
        fun from(type: StrategyType): StrategyTypeDto {
            return when (type) {
                StrategyType.Undefined -> TODO()
                StrategyType.FinalPriceBatingV1 -> FINAL_PRICE_BATING_V1
            }
        }
    }
}

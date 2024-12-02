package com.example.com.example.stockpurchaseservice.application.repository

import com.example.com.example.stockpurchaseservice.application.port.out.StockOrderPort
import com.example.com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import com.example.stockpurchaseservice.domain.Money
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.OrderId
import com.example.stockpurchaseservice.domain.PurchaseOrder
import com.example.stockpurchaseservice.domain.SellingOrder
import com.example.stockpurchaseservice.domain.StockId
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
        return stockOrderPort.findAllWithNotCompleted().map { orderDto ->
            orderDto.toOrder()
        }
    }
}

private fun Order.toDto(): OrderDto {
    var sellingAt: ZonedDateTime? = null // TODO: order에서 sellingAt가 not null일 경우가 있는가
    val sellingPrice = when (this) {
        is SellingOrder -> {
            this.sellingPrice.price
        }

        is PurchaseOrder -> {
            null
        }
    }

    return OrderDto(
        id = this.id.value,
        stockId = this.stockId.value,
        stockName = this.stockName,
        requestedAt = this.requestedAt,
        strategyType = StrategyTypeDto.from(this.strategyType),
        purchasedAt = this.purchasedAt,
        sellingAt = sellingAt,
        purchasePrice = this.purchasePrice.price,
        sellingPrice = sellingPrice,
        quantity = this.quantity,
    )
}

data class OrderDto(
    val id: UUID,
    val stockId: String,
    val stockName: String,
    val requestedAt: ZonedDateTime,
    val strategyType: StrategyTypeDto,
    val purchasedAt: ZonedDateTime?,
    val sellingAt: ZonedDateTime?,
    val purchasePrice: Double?,
    val sellingPrice: Double?,
    val quantity: Int,
) {
    fun toOrder(): SellingOrder {
        return SellingOrder.from(
            id = OrderId(id),
            stockId = StockId(stockId),
            stockName = stockName,
            requestedAt = requestedAt,
            strategyType = strategyType.toDomain(),
            sellingPrice = sellingPrice?.let { Money(sellingPrice) } ?: Money.undefined(),
            purchasePrice = purchasePrice?.let { Money(purchasePrice) } ?: Money.undefined(), // maping 확인 필요
            purchasedAt = purchasedAt ?: ZonedDateTime.now(), // maping 확인 필요
            quantity = quantity,
        )
    }
}

// TODO: common module로 이동 필요.
enum class StrategyTypeDto {
    FINAL_PRICE_BATING_V1,
    // 다른 타입 추가 가능
    ;

    fun toDomain(): StrategyType {
        return when (this) {
            FINAL_PRICE_BATING_V1 -> StrategyType.FinalPriceBatingV1
        }
    }

    companion object {
        fun from(type: StrategyType): StrategyTypeDto {
            return when (type) {
                StrategyType.Undefined -> TODO()
                StrategyType.FinalPriceBatingV1 -> FINAL_PRICE_BATING_V1
            }
        }
    }
}

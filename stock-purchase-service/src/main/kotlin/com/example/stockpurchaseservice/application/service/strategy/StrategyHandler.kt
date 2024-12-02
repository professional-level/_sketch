package com.example.stockpurchaseservice.application.service.strategy

import com.example.com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.service.BuyingStockPurchaseCommand
import com.example.stockpurchaseservice.application.service.BuyingStockPurchaseResult
import com.example.stockpurchaseservice.application.service.PurchaseStatus
import com.example.stockpurchaseservice.domain.FinalPriceBatingV1
import com.example.stockpurchaseservice.domain.PurchaseErrorCode
import com.example.stockpurchaseservice.domain.PurchaseFailedEvent
import com.example.stockpurchaseservice.domain.PurchaseOrder
import com.example.stockpurchaseservice.domain.PurchaseSuccessEvent
import com.example.stockpurchaseservice.domain.SellingOrder
import com.example.stockpurchaseservice.domain.Stock
import org.springframework.stereotype.Component

// 전략별 handler
@Component
internal class FinalPriceBatingV1Handler(
    private val marketService: MarketServicePort,
    private val orderRepository: StockOrderRepository,
) : StockPurchaseHandler<BuyingStockPurchaseCommand.OfFinalPriceBatingV1> {

    override suspend fun handle(command: BuyingStockPurchaseCommand.OfFinalPriceBatingV1): BuyingStockPurchaseResult {
        val stockId = command.stockId
        val stockName = command.stockName
        val requestedAt = command.requestAt
        val purchasePrice = command.targetPurchasePrice
        val stock = Stock(id = stockId.toDomain(), name = stockName)

        // 도메인 객체 생성
        val strategy = FinalPriceBatingV1.of(
            stock = stock,
            requestedAt = requestedAt,
            purchasePrice = purchasePrice,
        )

        // 구매 주문 생성
        val purchaseOrder = strategy.createPurchaseOrder().let(::checkNotNull) // TODO: check checkNotNull
        runCatching { marketService.buyStock(purchaseOrder.toDto()) }
            .onSuccess { purchaseOrder.success() }
            .onFailure { e ->
                purchaseOrder.failed(
                    message = e.message,
                    purchaseErrorCode = PurchaseErrorCode.UNDEFINED,
                )
            }

        // 이벤트 처리
        strategy.events.forEach { event ->
            // 이벤트 퍼블리싱 로직 추가
        }
        purchaseOrder.events.forEach { event ->
            // 이벤트 퍼블리싱 로직 추가
            when (event) {
                is PurchaseSuccessEvent -> orderRepository.save(purchaseOrder)
                is PurchaseFailedEvent -> {}
                else -> throw UndefinedException()
            }
        }

        return BuyingStockPurchaseResult(
            orderId = purchaseOrder.id,
            status = when (purchaseOrder.isSuccess) {
                true -> PurchaseStatus.CREATED
                false -> PurchaseStatus.FAILED
            },
        )
    }
}

internal fun PurchaseOrder.toDto(): PurchaseOrderDto {
    return PurchaseOrderDto(
        orderId = this.id.value,
        stockId = this.stockId.value,
        purchasePrice = this.purchasePrice.price,
    )
}

internal fun SellingOrder.toDto(): SellingOrderDto {
    return SellingOrderDto(
        orderId = this.id.value,
        stockId = this.stockId.value,
        sellingPrice = this.sellingPrice.price,
    )
}

// TODO: exception 고도화 및 패키지 위치 확인
class UndefinedException : RuntimeException()

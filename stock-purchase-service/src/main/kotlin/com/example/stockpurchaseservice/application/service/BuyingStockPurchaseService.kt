package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.BuyingStockPurchaseUseCase
import com.example.stockpurchaseservice.application.service.strategy.StockPurchaseHandlerFactory
import com.example.stockpurchaseservice.domain.Money
import com.example.stockpurchaseservice.domain.OrderId
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class BuyingStockPurchaseService(
    private val handlerFactory: StockPurchaseHandlerFactory,
) : BuyingStockPurchaseUseCase {

    override suspend fun execute(request: BuyingStockPurchaseCommand): BuyingStockPurchaseResult {
        val handler = handlerFactory.getHandler<BuyingStockPurchaseCommand>(request.type)
        return handler.handle(request)
    }
}

@JvmInline
value class StockId(val value: String) {
    fun toDomain(): com.example.stockpurchaseservice.domain.StockId {
        return com.example.stockpurchaseservice.domain.StockId(value)
    }
}

enum class StrategyType {
    Undefined,
    FinalPriceBatingV1,
    ;

    fun toDomain(): com.example.stockpurchaseservice.domain.StrategyType {
        return when (this) {
            FinalPriceBatingV1 -> com.example.stockpurchaseservice.domain.StrategyType.FinalPriceBatingV1
            Undefined -> com.example.stockpurchaseservice.domain.StrategyType.Undefined
        }
    }
}

sealed class BuyingStockPurchaseCommand(
    val stockId: StockId,
    val stockName: String,
    val requestAt: ZonedDateTime,
    val type: StrategyType,
) {
    class OfFinalPriceBatingV1(
        stockId: StockId,
        stockName: String,
        requestAt: ZonedDateTime,
        type: StrategyType,
        purchasePrice: Double,
    ) : BuyingStockPurchaseCommand(
        stockId = stockId,
        stockName = stockName,
        requestAt = requestAt,
        type = type,
    ) {
        internal val targetPurchasePrice: Money = Money(purchasePrice)

        init {
            require(type == StrategyType.FinalPriceBatingV1)
        }
    }
}

data class BuyingStockPurchaseResult(
    val orderId: OrderId,
    val status: PurchaseStatus,
)

enum class PurchaseStatus {
    CREATED,
    FAILED,
    COMPLETED,
}

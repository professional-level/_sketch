package com.example.stockpurchaseservice.application.service.strategy

import com.example.stockpurchaseservice.application.service.BuyingStockPurchaseCommand
import com.example.stockpurchaseservice.application.service.BuyingStockPurchaseResult
import com.example.stockpurchaseservice.application.service.StrategyType
import org.springframework.stereotype.Component

interface StockPurchaseHandler<T : BuyingStockPurchaseCommand> {
    fun handle(command: T): BuyingStockPurchaseResult
}

@Component
class StockPurchaseHandlerFactory(
    private val handlers: List<StockPurchaseHandler<out BuyingStockPurchaseCommand>>,
) {

    private val handlerMap: Map<StrategyType, StockPurchaseHandler<out BuyingStockPurchaseCommand>> =
        handlers.associateBy { handler ->
            when (handler) {
                is FinalPriceBatingV1Handler -> StrategyType.FinalPriceBatingV1
                // 다른 전략 핸들러를 추가할 수 있습니다.
                else -> throw IllegalArgumentException("지원하지 않는 핸들러 타입입니다: ${handler::class}")
            }
        }

    @Suppress("UNCHECKED_CAST")
    fun <T : BuyingStockPurchaseCommand> getHandler(strategyType: StrategyType): StockPurchaseHandler<T> {
        return handlerMap[strategyType] as? StockPurchaseHandler<T>
            ?: throw IllegalArgumentException("전략 타입에 대한 핸들러를 찾을 수 없습니다: $strategyType")
    }
}
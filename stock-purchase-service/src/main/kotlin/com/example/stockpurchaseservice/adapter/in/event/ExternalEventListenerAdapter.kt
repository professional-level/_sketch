package com.example.com.example.stockpurchaseservice.adapter.`in`.event

import com.example.common.ExternalApiAdapter
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@ExternalApiAdapter // TODO: 정확한 annotation 매칭
internal class ExternalEventListenerAdapter(
    private val buyingStockPurchaseUseCase: BuyingStockPurchaseUseCase
) {
    @KafkaListener(topics = ["strategies-saved-topic"]) // TODO: topic enum으로 관리하며 common으로 이동 필요
    fun createdStrategies(message: String) {
        // 구현
        println(message)
        buyingStockPurchaseUseCase.execute(BuyingStockPurchaseRequest("temp"))
    }
}

typealias BuyingStockPurchaseUseCase = StockPurchaseUseCase<BuyingStockPurchaseRequest,BuyingStockPurchaseResult>
interface StockPurchaseUseCase<T, R> : UseCase<T, R>

interface UseCase<T,R> {
    fun execute(request: T) : R
}

@Service
class BuyingStockPurchaseService: BuyingStockPurchaseUseCase{
    override fun execute(request: BuyingStockPurchaseRequest): BuyingStockPurchaseResult {
        TODO("Not yet implemented")
    }
}

data class BuyingStockPurchaseRequest(
    val temp: String
)
data class BuyingStockPurchaseResult(
    val temp: String
)
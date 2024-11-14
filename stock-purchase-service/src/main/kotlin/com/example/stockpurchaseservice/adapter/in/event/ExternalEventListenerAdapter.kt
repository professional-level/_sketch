package com.example.com.example.stockpurchaseservice.adapter.`in`.event

import Event
import com.example.com.example.stockpurchaseservice.application.port.`in`.BuyingStockPurchaseUseCase
import com.example.com.example.stockpurchaseservice.application.service.BuyingStockPurchaseCommand
import com.example.com.example.stockpurchaseservice.application.service.StockId
import com.example.com.example.stockpurchaseservice.application.service.StrategyType
import com.example.common.ExternalApiAdapter
import common.ConsumerGroupId.PURCHASE_SERVICE
import common.Topic.STRATEGY_SAVED
import common.proto.ProtoUtils.toZonedDateTime
import org.springframework.kafka.annotation.KafkaListener

@ExternalApiAdapter // TODO: 정확한 annotation 매칭
internal class ExternalEventListenerAdapter(
    private val buyingStockPurchaseUseCase: BuyingStockPurchaseUseCase, // TODO: ServiceMapper class 필요
) {
    @KafkaListener(topics = [STRATEGY_SAVED], groupId = PURCHASE_SERVICE) // TODO: topic enum으로 관리하며 common으로 이동 필요
    fun createdStrategies(message: ByteArray ) {
        // 구현
        println(message) // TODO: 테스트 후 제거
        val tempMessage: ByteArray = byteArrayOf()
        val event = Event.StrategySavedEvent.parseFrom(tempMessage)
        buyingStockPurchaseUseCase.execute(event.toCommand())
    }
}

// 확장함수
fun Event.StrategyType.convert(): StrategyType {
    return when (this) {
        Event.StrategyType.UNDEFINED -> StrategyType.Undefined // TODO: UNRECOGNIZED 사용하도록 변경 필요
        Event.StrategyType.FINAL_PRICE_BATING_V1 -> StrategyType.FinalPriceBatingV1
        Event.StrategyType.UNRECOGNIZED -> StrategyType.Undefined
    }
}

fun Event.StrategySavedEvent.toCommand(): BuyingStockPurchaseCommand {
    val event = this
    val strategyType = event.type.convert()
    return when (strategyType) {
        StrategyType.FinalPriceBatingV1 -> BuyingStockPurchaseCommand.OfFinalPriceBatingV1(
            stockId = StockId(event.stockId),
            stockName = event.stockName,
            requestAt = event.savedAt.toZonedDateTime(),
            type = strategyType,
            purchasePrice = 0.0 // TODO: 실제 구매 가격으로 변경 필요
        )

        else -> throw IllegalArgumentException("지원하지 않는 전략 타입입니다: $strategyType")
    }
}
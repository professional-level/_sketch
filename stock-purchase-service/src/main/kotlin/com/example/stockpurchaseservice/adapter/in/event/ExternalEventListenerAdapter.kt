package com.example.com.example.stockpurchaseservice.adapter.`in`.event

import Event
import com.example.common.ExternalApiAdapter
import com.google.protobuf.Timestamp
import common.Topic.STRATEGY_SAVED
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@ExternalApiAdapter // TODO: 정확한 annotation 매칭
internal class ExternalEventListenerAdapter(
    private val buyingStockPurchaseUseCase: BuyingStockPurchaseUseCase,
) {
    @KafkaListener(topics = [STRATEGY_SAVED]) // TODO: topic enum으로 관리하며 common으로 이동 필요
    fun createdStrategies(message: String) {
        // 구현
        println(message)
        val tempMessage: ByteArray = byteArrayOf()
        val event = Event.StrategiesSavedEvent.parseFrom(tempMessage)
        buyingStockPurchaseUseCase.execute(event.toCommand())
    }
}

interface BuyingStockPurchaseUseCase : StockPurchaseUseCase<BuyingStockPurchaseCommand, BuyingStockPurchaseResult>
interface StockPurchaseUseCase<T, R> : UseCase<T, R>

// TODO: 사용해 보고 큰 문제 없으면 common으로 이동
interface UseCase<T, R> {
    fun execute(request: T): R
}

@Service
class BuyingStockPurchaseService : BuyingStockPurchaseUseCase {
    override fun execute(request: BuyingStockPurchaseCommand): BuyingStockPurchaseResult {
        TODO("Not yet implemented")
    }
}
fun Event.StrategiesSavedEvent.toCommand(): BuyingStockPurchaseCommand {
   return BuyingStockPurchaseCommand(
        stockId = stockId,
        requestAt = savedAt.toZonedDateTime(),
        type = type.convert()
    )
}

data class BuyingStockPurchaseResult(
    val temp: String,
)

class BuyingStockPurchaseCommand(
    stockId: String,
    val requestAt: ZonedDateTime,
    val type: StrategyType,
) {
    val stockId: StockId = StockId(stockId)
}

@JvmInline
value class StockId(val value: String)

enum class StrategyType {
    Undefined,
    FinalPriceBatingV1
}

// 확장함수
fun Event.StrategyType.convert(): StrategyType {
    return when (this) {
        Event.StrategyType.UNDEFINED -> StrategyType.Undefined // TODO: UNRECOGNIZED 사용하도록 변경 필요
        Event.StrategyType.FINAL_PRICE_BATING_V1 -> StrategyType.FinalPriceBatingV1
        Event.StrategyType.UNRECOGNIZED -> StrategyType.Undefined
    }
}

// protobuff 용 timestamp 값을 convert
fun Timestamp.toZonedDateTime(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime {
    val instant = Instant.ofEpochSecond(this.seconds, this.nanos.toLong())
    return ZonedDateTime.ofInstant(instant, zone)
}
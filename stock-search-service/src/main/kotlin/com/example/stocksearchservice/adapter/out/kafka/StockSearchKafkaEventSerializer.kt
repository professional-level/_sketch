package com.example.stocksearchservice.adapter.out.kafka

import Event
import com.example.common.application.event.ApplicationEvent
import com.example.stocksearchservice.application.event.StrategyCreatedApplicationEvent
import com.example.stocksearchservice.application.event.StrategyTypeDto
import common.proto.ProtoUtils.getMeta
import common.proto.ProtoUtils.toProtobufTimestamp
import org.springframework.stereotype.Component

@Component
internal class StockSearchKafkaEventSerializer {
    fun serialize(event: ApplicationEvent): ByteArray {
        return when (event) {
            is StrategyCreatedApplicationEvent -> event.toProto()
            else -> throw IllegalArgumentException("Unsupported event type: ${event::class.java}")
        }.toByteArray()
    }

    private fun StrategyCreatedApplicationEvent.toProto(): Event.StrategySavedEvent {
        return Event.StrategySavedEvent.newBuilder()
            .setStockId(this.stockId)
            .setStockName(this.stockName)
            .setMeta(this.getMeta())
            .setSavedAt(this.savedAt.toProtobufTimestamp())
            .setType(this.toProtoStrategyType())
            .setEventId(this.id.toString())
            .setStrategyId(this.strategyId)
            .setIdempotencyKey(this.id.toString())
            .setStrategyVersion(this.strategyVersion)
            .setDecisionPrice(this.decisionPrice)
            .setTargetBuyPrice(this.targetBuyPrice)
            .setBudget(this.budget)
            .setQuantityPolicy(this.quantityPolicy)
            .build()
    }

    private fun StrategyCreatedApplicationEvent.toProtoStrategyType() = when (this.type) {
        StrategyTypeDto.FINAL_PRICE_BATING_V1 -> Event.StrategyType.FINAL_PRICE_BATING_V1
    }
}

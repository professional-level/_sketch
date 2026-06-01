package com.example.stocksearchservice.adapter.out.kafka

import Event
import com.example.common.application.event.ApplicationEvent
import com.example.stocksearchservice.application.event.StrategyCreatedApplicationEvent
import com.example.stocksearchservice.application.event.StrategyTypeDto
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

    private fun StrategyCreatedApplicationEvent.toProto(): Event.StrategyExecutionStartRequested {
        return Event.StrategyExecutionStartRequested.newBuilder()
            .setEventId(this.id.toString())
            .setIdempotencyKey(this.startRequestIdempotencyKey())
            .setStrategyType(this.toProtoStrategyExecutionType())
            .setStrategyVersion(this.strategyVersion)
            .setSymbol(this.stockId)
            .setMarket(this.stockId.toMarket())
            .setBudget(this.budget)
            .setSourceService("stock-search-service")
            .setSourceSignalId(this.strategyId)
            .setRequestedAt(this.savedAt.toProtobufTimestamp())
            .setParameters(
                Event.StrategyExecutionParameters.newBuilder()
                    .setFinalPriceBatingV1(
                        Event.FinalPriceBatingV1StartParameters.newBuilder()
                            .setTargetBuyPrice(this.targetBuyPrice.takeIf { it > 0.0 } ?: this.decisionPrice)
                            .setBudget(this.budget)
                            .setQuantityPolicy(this.quantityPolicy),
                    ),
            )
            .setMeta(
                Event.EventMeta.newBuilder()
                    .setOccurredAt(this.occurredAt.toProtobufTimestamp())
                    .setServiceName("stock-search-service"),
            )
            .build()
    }

    private fun StrategyCreatedApplicationEvent.toProtoStrategyExecutionType() = when (this.type) {
        StrategyTypeDto.FINAL_PRICE_BATING_V1 -> Event.StrategyExecutionType.FINAL_PRICE_BATING_V1_STRATEGY
    }

    private fun StrategyCreatedApplicationEvent.startRequestIdempotencyKey(): String {
        return "${type.name}:$stockId:${savedAt.toLocalDate()}"
    }

    private fun String.toMarket(): String {
        return if (length == 6 && all(Char::isDigit)) "KRX" else "US"
    }
}

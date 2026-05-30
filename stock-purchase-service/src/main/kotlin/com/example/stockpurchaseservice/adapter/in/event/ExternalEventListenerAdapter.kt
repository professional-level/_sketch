package com.example.stockpurchaseservice.adapter.`in`.event

import Event
import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.`in`.BuyingStockPurchaseUseCase
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentUseCase
import com.example.stockpurchaseservice.application.service.BuyingStockPurchaseCommand
import com.example.stockpurchaseservice.application.service.StockId
import com.example.stockpurchaseservice.application.service.StrategyType
import common.ConsumerGroupId.PURCHASE_SERVICE
import common.Topic.ORDER_INTENT_CREATED
import common.Topic.STRATEGY_SAVED
import common.proto.ProtoUtils.toZonedDateTime
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.springframework.kafka.annotation.KafkaListener

@ExternalApiAdapter // TODO: verify whether a dedicated inbound event adapter stereotype is needed.
internal class ExternalEventListenerAdapter(
    private val buyingStockPurchaseUseCase: BuyingStockPurchaseUseCase, // TODO: extract event-to-command mapper if this listener keeps growing.
    private val submitOrderIntentUseCase: SubmitOrderIntentUseCase,
) {
    @KafkaListener(topics = [STRATEGY_SAVED], groupId = PURCHASE_SERVICE)
    suspend fun createdStrategies(message: ByteArray) {
        // TODO: publish or persist an application event that records successful event consumption.
        val event = Event.StrategySavedEvent.parseFrom(message)
        buyingStockPurchaseUseCase.execute(event.toCommand())
    }

    @KafkaListener(topics = [ORDER_INTENT_CREATED], groupId = PURCHASE_SERVICE)
    suspend fun orderIntents(message: ByteArray) {
        val event = Event.OrderIntentCreatedEvent.parseFrom(message)
        submitOrderIntentUseCase.execute(event.toCommand())
    }
}

fun Event.StrategyType.convert(): StrategyType {
    return when (this) {
        Event.StrategyType.UNDEFINED -> StrategyType.Undefined // TODO: decide explicit UNRECOGNIZED handling.
        Event.StrategyType.FINAL_PRICE_BATING_V1 -> StrategyType.FinalPriceBatingV1
        Event.StrategyType.UNRECOGNIZED -> StrategyType.Undefined
    }
}

fun Event.StrategySavedEvent.toCommand(): BuyingStockPurchaseCommand {
    val event = this
    val strategyType = event.type.convert()
    val eventId = event.eventId.toUuidOrDeterministic {
        "${event.stockId}:${event.type.name}:${event.savedAt.seconds}:${event.savedAt.nanos}"
    }
    val strategyId = event.strategyId.ifBlank { "${strategyType}:${event.stockId}" }
    val idempotencyKey = event.idempotencyKey.ifBlank { eventId.toString() }
    val strategyVersion = event.strategyVersion.ifBlank { "v1" }
    val targetBuyPrice = event.targetBuyPrice.takeIf { it > 0.0 } ?: event.decisionPrice
    return when (strategyType) {
        StrategyType.FinalPriceBatingV1 -> BuyingStockPurchaseCommand.OfFinalPriceBatingV1(
            stockId = StockId(event.stockId),
            stockName = event.stockName,
            requestAt = event.savedAt.toZonedDateTime(),
            type = strategyType,
            purchasePrice = targetBuyPrice,
            eventId = eventId,
            strategyId = strategyId,
            idempotencyKey = idempotencyKey,
            strategyVersion = strategyVersion,
            budget = event.budget,
            quantityPolicy = event.quantityPolicy,
        )

        else -> throw IllegalArgumentException("unsupported strategy type: $strategyType")
    }
}

fun Event.OrderIntentCreatedEvent.toCommand(): SubmitOrderIntentCommand {
    val event = this
    val eventId = event.eventId.toUuidOrDeterministic {
        "${event.strategyExecutionId}:${event.orderTag}:${event.createdAt.seconds}:${event.createdAt.nanos}"
    }
    return SubmitOrderIntentCommand(
        eventId = eventId,
        idempotencyKey = event.idempotencyKey.ifBlank { eventId.toString() },
        strategyExecutionId = event.strategyExecutionId,
        symbol = event.symbol,
        side = event.side.convert(),
        orderType = event.orderType.convert(),
        price = event.price.takeIf { it > 0.0 },
        quantity = event.quantity,
        orderTag = event.orderTag,
        createdAt = event.createdAt.toZonedDateTime(),
    )
}

private fun Event.OrderIntentSide.convert(): OrderIntentSide {
    return when (this) {
        Event.OrderIntentSide.ORDER_INTENT_BUY -> OrderIntentSide.BUY
        Event.OrderIntentSide.ORDER_INTENT_SELL -> OrderIntentSide.SELL
        Event.OrderIntentSide.ORDER_INTENT_SIDE_UNDEFINED,
        Event.OrderIntentSide.UNRECOGNIZED -> throw IllegalArgumentException("unsupported order intent side: $this")
    }
}

private fun Event.OrderIntentOrderType.convert(): OrderIntentType {
    return when (this) {
        Event.OrderIntentOrderType.ORDER_INTENT_LOC -> OrderIntentType.LOC
        Event.OrderIntentOrderType.ORDER_INTENT_MOC -> OrderIntentType.MOC
        Event.OrderIntentOrderType.ORDER_INTENT_LIMIT -> OrderIntentType.LIMIT
        Event.OrderIntentOrderType.ORDER_INTENT_ORDER_TYPE_UNDEFINED,
        Event.OrderIntentOrderType.UNRECOGNIZED -> throw IllegalArgumentException("unsupported order intent type: $this")
    }
}

private fun String.toUuidOrDeterministic(seed: () -> String): UUID {
    return takeIf { it.isNotBlank() }
        ?.let(UUID::fromString)
        ?: UUID.nameUUIDFromBytes(seed().toByteArray(StandardCharsets.UTF_8))
}

package com.example.stockpurchaseservice.adapter.`in`.event

import Event
import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentUseCase
import common.ConsumerGroupId.PURCHASE_SERVICE
import common.Topic.ORDER_INTENT_CREATED
import common.proto.ProtoUtils.toZonedDateTime
import org.springframework.kafka.annotation.KafkaListener
import java.nio.charset.StandardCharsets
import java.util.UUID

@ExternalApiAdapter // TODO: verify whether a dedicated inbound event adapter stereotype is needed.
internal class ExternalEventListenerAdapter(
    private val submitOrderIntentUseCase: SubmitOrderIntentUseCase,
) {
    @KafkaListener(topics = [ORDER_INTENT_CREATED], groupId = PURCHASE_SERVICE)
    suspend fun orderIntents(message: ByteArray) {
        val event = Event.OrderIntentCreatedEvent.parseFrom(message)
        submitOrderIntentUseCase.execute(event.toCommand())
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

package com.example.strategyexecutionservice.adapter.`in`.event

import Event
import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillCommand
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillUseCase
import com.example.strategyexecutionservice.application.port.`in`.OrderFillKind
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventCommand
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventUseCase
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionUseCase
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import common.ConsumerGroupId.STRATEGY_EXECUTION_SERVICE
import common.Topic.ORDER_FILLED
import common.Topic.ORDER_PARTIALLY_FILLED
import common.Topic.ORDER_REJECTED
import common.Topic.ORDER_SUBMITTED
import common.Topic.STRATEGY_EXECUTION_START_REQUESTED
import common.proto.ProtoUtils.toZonedDateTime
import org.springframework.kafka.annotation.KafkaListener

@ExternalApiAdapter
internal class StrategyExecutionEventListenerAdapter(
    private val startStrategyExecutionUseCase: StartStrategyExecutionUseCase,
    private val applyOrderFillUseCase: ApplyOrderFillUseCase,
    private val recordOrderExecutionEventUseCase: RecordOrderExecutionEventUseCase,
) {
    @KafkaListener(topics = [STRATEGY_EXECUTION_START_REQUESTED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun strategyExecutionStartRequests(message: ByteArray) {
        val event = Event.StrategyExecutionStartRequested.parseFrom(message)
        startStrategyExecutionUseCase.execute(event.toCommand())
    }

    @KafkaListener(topics = [ORDER_FILLED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun orderFills(message: ByteArray) {
        val event = Event.OrderFilled.parseFrom(message)
        applyOrderFillUseCase.execute(event.toCommand())
    }

    @KafkaListener(topics = [ORDER_PARTIALLY_FILLED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun partialOrderFills(message: ByteArray) {
        val event = Event.OrderPartiallyFilled.parseFrom(message)
        applyOrderFillUseCase.execute(event.toCommand())
    }

    @KafkaListener(topics = [ORDER_SUBMITTED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun orderSubmissions(message: ByteArray) {
        val event = Event.OrderSubmitted.parseFrom(message)
        recordOrderExecutionEventUseCase.execute(event.toCommand())
    }

    @KafkaListener(topics = [ORDER_REJECTED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun orderRejections(message: ByteArray) {
        val event = Event.OrderRejected.parseFrom(message)
        recordOrderExecutionEventUseCase.execute(event.toCommand())
    }
}

private fun Event.StrategyExecutionStartRequested.toCommand(): StartStrategyExecutionCommand {
    val event = this
    val idempotencyKey = event.idempotencyKey.ifBlank { event.eventId }
    val strategyVersion = event.strategyVersion.ifBlank { "v1" }
    val requestedAt = event.requestedAt.toZonedDateTime()
    return when (event.strategyType) {
        Event.StrategyExecutionType.LAOR_V4_STRATEGY -> {
            val parameters = event.parameters.laorV4
            val strategySymbol = LaorV4StrategySymbol.valueOf(event.symbol.uppercase())
            StartStrategyExecutionCommand.LaorV4(
                executionId = event.sourceSignalId.ifBlank { "laor-v4:${strategySymbol.ticker}" },
                idempotencyKey = idempotencyKey,
                strategyVersion = strategyVersion,
                strategySymbol = strategySymbol,
                market = event.market.ifBlank { "US" },
                budget = event.budget,
                totalSplitCount = parameters.totalSplitCount.takeIf { it > 0 } ?: 20,
                firstBuyLimitMultiplier = parameters.firstBuyLimitMultiplier
                    .takeIf { it > 0.0 }
                    ?: LaorV4StrategyConfig.DEFAULT_FIRST_BUY_LIMIT_MULTIPLIER,
                autoRestart = parameters.autoRestart,
                requestedAt = requestedAt,
            )
        }

        Event.StrategyExecutionType.FINAL_PRICE_BATING_V1_STRATEGY -> {
            val parameters = event.parameters.finalPriceBatingV1
            StartStrategyExecutionCommand.FinalPriceBatingV1(
                executionId = event.sourceSignalId.ifBlank {
                    "final-price-bating-v1:${event.symbol}:${requestedAt.toLocalDate()}"
                },
                idempotencyKey = idempotencyKey,
                strategyVersion = strategyVersion,
                symbol = event.symbol,
                market = event.market.ifBlank { event.symbol.toMarket() },
                budget = parameters.budget.takeIf { it > 0.0 } ?: event.budget,
                targetBuyPrice = parameters.targetBuyPrice,
                quantityPolicy = parameters.quantityPolicy.ifBlank { "BUDGET_DIVIDED_BY_TARGET_BUY_PRICE" },
                requestedAt = requestedAt,
            )
        }

        Event.StrategyExecutionType.STRATEGY_EXECUTION_TYPE_UNDEFINED,
        null,
        Event.StrategyExecutionType.UNRECOGNIZED -> {
            throw IllegalArgumentException("unsupported strategy execution type: ${event.strategyType}")
        }
    }
}

private fun Event.OrderFilled.toCommand(): ApplyOrderFillCommand {
    return ApplyOrderFillCommand(
        eventId = eventId,
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId,
        side = side.toDomain(),
        fillKind = OrderFillKind.FILLED,
        filledPrice = filledPrice,
        filledQuantity = filledQuantity,
        orderTag = orderTag,
        filledAt = filledAt.toZonedDateTime(),
    )
}

private fun Event.OrderPartiallyFilled.toCommand(): ApplyOrderFillCommand {
    return ApplyOrderFillCommand(
        eventId = eventId,
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId,
        side = side.toDomain(),
        fillKind = OrderFillKind.PARTIALLY_FILLED,
        filledPrice = filledPrice,
        filledQuantity = filledQuantity,
        orderTag = orderTag,
        filledAt = filledAt.toZonedDateTime(),
    )
}

private fun Event.OrderSubmitted.toCommand(): RecordOrderExecutionEventCommand.Submitted {
    return RecordOrderExecutionEventCommand.Submitted(
        eventId = eventId,
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId,
        submittedAt = submittedAt.toZonedDateTime(),
    )
}

private fun Event.OrderRejected.toCommand(): RecordOrderExecutionEventCommand.Rejected {
    return RecordOrderExecutionEventCommand.Rejected(
        eventId = eventId,
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId.ifBlank { null },
        reason = reason,
        rejectedAt = rejectedAt.toZonedDateTime(),
    )
}

private fun Event.OrderIntentSide.toDomain(): OrderSide {
    return when (this) {
        Event.OrderIntentSide.ORDER_INTENT_BUY -> OrderSide.BUY
        Event.OrderIntentSide.ORDER_INTENT_SELL -> OrderSide.SELL
        Event.OrderIntentSide.ORDER_INTENT_SIDE_UNDEFINED,
        Event.OrderIntentSide.UNRECOGNIZED -> throw IllegalArgumentException("unsupported order side: $this")
    }
}

private fun String.toMarket(): String {
    return if (length == 6 && all(Char::isDigit)) "KRX" else "US"
}

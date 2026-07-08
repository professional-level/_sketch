package com.example.strategyexecutionservice.adapter.`in`.event

import Event
import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillCommand
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillUseCase
import com.example.strategyexecutionservice.application.port.`in`.OrderFillKind
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyCommand
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyUseCase
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventCommand
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventUseCase
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneCommand
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneUseCase
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionUseCase
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import common.ConsumerGroupId.STRATEGY_EXECUTION_SERVICE
import common.Topic.LAOR_ORDER_ANOMALY_DETECTED
import common.Topic.LAOR_ORDER_MILESTONE_DETECTED
import common.Topic.ORDER_FILLED
import common.Topic.ORDER_PARTIALLY_FILLED
import common.Topic.ORDER_CANCELLED
import common.Topic.ORDER_REJECTED
import common.Topic.ORDER_SUBMITTED
import common.Topic.STRATEGY_EXECUTION_START_REQUESTED
import common.observability.TraceContext
import common.proto.ProtoUtils.toZonedDateTime
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import java.nio.charset.StandardCharsets

@ExternalApiAdapter
internal class StrategyExecutionEventListenerAdapter(
    private val startStrategyExecutionUseCase: StartStrategyExecutionUseCase,
    private val applyOrderFillUseCase: ApplyOrderFillUseCase,
    private val recordOrderExecutionEventUseCase: RecordOrderExecutionEventUseCase,
    private val signalLaorOrderMilestoneUseCase: SignalLaorOrderMilestoneUseCase,
    private val recordLaorOrderAnomalyUseCase: RecordLaorOrderAnomalyUseCase,
) {
    @KafkaListener(topics = [STRATEGY_EXECUTION_START_REQUESTED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun strategyExecutionStartRequests(record: ConsumerRecord<String, ByteArray>) {
        record.withTraceContext {
            val event = Event.StrategyExecutionStartRequested.parseFrom(record.value())
            startStrategyExecutionUseCase.execute(event.toCommand())
        }
    }

    @KafkaListener(topics = [ORDER_FILLED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun orderFills(record: ConsumerRecord<String, ByteArray>) {
        record.withTraceContext {
            val event = Event.OrderFilled.parseFrom(record.value())
            applyOrderFillUseCase.execute(event.toCommand())
        }
    }

    @KafkaListener(topics = [ORDER_PARTIALLY_FILLED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun partialOrderFills(record: ConsumerRecord<String, ByteArray>) {
        record.withTraceContext {
            val event = Event.OrderPartiallyFilled.parseFrom(record.value())
            applyOrderFillUseCase.execute(event.toCommand())
        }
    }

    @KafkaListener(topics = [ORDER_SUBMITTED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun orderSubmissions(record: ConsumerRecord<String, ByteArray>) {
        record.withTraceContext {
            val event = Event.OrderSubmitted.parseFrom(record.value())
            recordOrderExecutionEventUseCase.execute(event.toCommand())
        }
    }

    @KafkaListener(topics = [ORDER_REJECTED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun orderRejections(record: ConsumerRecord<String, ByteArray>) {
        record.withTraceContext {
            val event = Event.OrderRejected.parseFrom(record.value())
            recordOrderExecutionEventUseCase.execute(event.toCommand())
        }
    }

    @KafkaListener(topics = [ORDER_CANCELLED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun orderCancellations(record: ConsumerRecord<String, ByteArray>) {
        record.withTraceContext {
            val event = Event.OrderCancelled.parseFrom(record.value())
            recordOrderExecutionEventUseCase.execute(event.toCommand())
        }
    }

    @KafkaListener(topics = [LAOR_ORDER_MILESTONE_DETECTED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun laorOrderMilestones(record: ConsumerRecord<String, ByteArray>) {
        record.withTraceContext {
            val event = Event.LaorOrderMilestoneDetected.parseFrom(record.value())
            signalLaorOrderMilestoneUseCase.execute(event.toCommand())
        }
    }

    @KafkaListener(topics = [LAOR_ORDER_ANOMALY_DETECTED], groupId = STRATEGY_EXECUTION_SERVICE)
    suspend fun laorOrderAnomalies(record: ConsumerRecord<String, ByteArray>) {
        record.withTraceContext {
            val event = Event.LaorOrderAnomalyDetected.parseFrom(record.value())
            recordLaorOrderAnomalyUseCase.execute(event.toCommand())
        }
    }
}

private suspend fun ConsumerRecord<*, ByteArray>.withTraceContext(block: suspend () -> Unit) {
    val traceContext = traceContext()
    if (traceContext.isEmpty()) {
        block()
    } else {
        traceContext.withMdc(block)
    }
}

private fun ConsumerRecord<*, *>.traceContext(): TraceContext {
    return TraceContext.fromHeaders(
        traceParent = headerValue(TraceContext.TRACEPARENT_KEY),
        traceId = headerValue(TraceContext.TRACE_ID_KEY),
        spanId = headerValue(TraceContext.SPAN_ID_KEY),
    )
}

private fun ConsumerRecord<*, *>.headerValue(key: String): String? {
    return headers().lastHeader(key)?.value()?.toString(StandardCharsets.UTF_8)
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
                totalSplitCount = parameters.totalSplitCount,
                firstBuyLimitPercentAbovePreviousClose = parameters.firstBuyLimitPercentAbovePreviousClose,
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
        idempotencyKey = idempotencyKey.ifBlank { eventId },
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
        idempotencyKey = idempotencyKey.ifBlank { eventId },
    )
}

private fun Event.OrderSubmitted.toCommand(): RecordOrderExecutionEventCommand.Submitted {
    return RecordOrderExecutionEventCommand.Submitted(
        eventId = eventId,
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId,
        side = side.toDomainOrNull(),
        orderTag = orderTag.ifBlank { null },
        quantity = quantity.takeIf { it > 0 },
        submittedAt = submittedAt.toZonedDateTime(),
        idempotencyKey = idempotencyKey.ifBlank { eventId },
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
        idempotencyKey = idempotencyKey.ifBlank { eventId },
    )
}

private fun Event.OrderCancelled.toCommand(): RecordOrderExecutionEventCommand.Cancelled {
    return RecordOrderExecutionEventCommand.Cancelled(
        eventId = eventId,
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId,
        reason = reason,
        cancelledAt = cancelledAt.toZonedDateTime(),
        idempotencyKey = idempotencyKey.ifBlank { eventId },
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

private fun Event.OrderIntentSide.toDomainOrNull(): OrderSide? {
    return when (this) {
        Event.OrderIntentSide.ORDER_INTENT_BUY -> OrderSide.BUY
        Event.OrderIntentSide.ORDER_INTENT_SELL -> OrderSide.SELL
        Event.OrderIntentSide.ORDER_INTENT_SIDE_UNDEFINED,
        Event.OrderIntentSide.UNRECOGNIZED -> null
    }
}

private fun Event.LaorOrderMilestoneDetected.toCommand(): SignalLaorOrderMilestoneCommand {
    return SignalLaorOrderMilestoneCommand(
        eventId = eventId,
        milestoneType = milestoneType.toSignalName(),
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId.ifBlank { null },
        orderTag = orderTag.ifBlank { null },
        side = side.toDomainOrNull(),
        filledQuantity = filledQuantity,
        averageFilledPrice = averageFilledPrice.takeIf { it > 0.0 },
        occurredAt = occurredAt.toZonedDateTime(),
        sourceEventIds = sourceEventIdsList,
        idempotencyKey = idempotencyKey.ifBlank { eventId },
    )
}

private fun Event.LaorOrderAnomalyDetected.toCommand(): RecordLaorOrderAnomalyCommand {
    return RecordLaorOrderAnomalyCommand(
        eventId = eventId,
        anomalyType = anomalyType.toRecordName(),
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId.ifBlank { null },
        orderTag = orderTag.ifBlank { null },
        side = side.toDomainOrNull(),
        reason = reason,
        occurredAt = occurredAt.toZonedDateTime(),
        sourceEventIds = sourceEventIdsList,
        idempotencyKey = idempotencyKey.ifBlank { eventId },
    )
}

private fun Event.LaorOrderMilestoneType.toSignalName(): String {
    return when (this) {
        Event.LaorOrderMilestoneType.ENTRY_BUY_SUBMITTED,
        Event.LaorOrderMilestoneType.ENTRY_BUY_PARTIALLY_FILLED,
        Event.LaorOrderMilestoneType.ENTRY_BUY_FILLED,
        Event.LaorOrderMilestoneType.EXIT_SELL_SUBMITTED,
        Event.LaorOrderMilestoneType.EXIT_SELL_PARTIALLY_FILLED,
        Event.LaorOrderMilestoneType.EXIT_SELL_FILLED,
        Event.LaorOrderMilestoneType.ORDER_REJECTED,
        Event.LaorOrderMilestoneType.ORDER_CANCELLED,
        Event.LaorOrderMilestoneType.ORDER_FILL_TIMEOUT_DETECTED -> name
        Event.LaorOrderMilestoneType.LAOR_ORDER_MILESTONE_TYPE_UNDEFINED,
        Event.LaorOrderMilestoneType.UNRECOGNIZED -> {
            throw IllegalArgumentException("unsupported laor order milestone type: $this")
        }
    }
}

private fun Event.LaorOrderAnomalyType.toRecordName(): String {
    return when (this) {
        Event.LaorOrderAnomalyType.FILL_BEFORE_SUBMIT,
        Event.LaorOrderAnomalyType.DUPLICATE_TERMINAL_EVENT,
        Event.LaorOrderAnomalyType.FILLED_QUANTITY_EXCEEDS_EXPECTED,
        Event.LaorOrderAnomalyType.CONFLICTING_BROKER_ORDER_ID,
        Event.LaorOrderAnomalyType.UNKNOWN_ORDER_INTENT,
        Event.LaorOrderAnomalyType.LATE_EVENT_AFTER_TERMINAL -> name
        Event.LaorOrderAnomalyType.LAOR_ORDER_ANOMALY_TYPE_UNDEFINED,
        Event.LaorOrderAnomalyType.UNRECOGNIZED -> {
            throw IllegalArgumentException("unsupported laor order anomaly type: $this")
        }
    }
}

private fun String.toMarket(): String {
    return if (length == 6 && all(Char::isDigit)) "KRX" else "US"
}

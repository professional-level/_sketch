package com.example.streamprocessingservice.laor.application

import java.time.Duration
import java.time.ZonedDateTime

class OrderLifecycleProcessor(
    private val fillTimeout: Duration = Duration.ofMinutes(DEFAULT_FILL_TIMEOUT_MINUTES),
) {
    fun process(
        event: OrderExecutionEvent,
        current: OrderLifecycleState?,
    ): ProcessingResult {
        val initialState = current ?: event.initialState()
        val outputs = mutableListOf<LifecycleEventEnvelope>()

        if (event.type == OrderExecutionEventType.INTENT_CREATED && event.strategyKind == StrategyExecutionKind.OTHER) {
            return ProcessingResult(state = initialState.mergeIntent(event), outputs = emptyList())
        }

        if (initialState.strategyKind == StrategyExecutionKind.OTHER) {
            return ProcessingResult(state = initialState.updateLastEvent(event), outputs = emptyList())
        }

        if (current == null && event.type != OrderExecutionEventType.INTENT_CREATED) {
            outputs += anomaly(
                type = AnomalyType.UNKNOWN_ORDER_INTENT,
                state = initialState,
                event = event,
                reason = "order execution event arrived before order intent metadata",
            )
        }

        if (initialState.hasTerminalStatus() && event.type != OrderExecutionEventType.INTENT_CREATED) {
            if (event.type.isTerminalEvent()) {
                outputs += anomaly(
                    type = AnomalyType.DUPLICATE_TERMINAL_EVENT,
                    state = initialState,
                    event = event,
                    reason = "terminal event arrived after terminal status ${initialState.terminalStatus}",
                )
            }
            outputs += anomaly(
                type = AnomalyType.LATE_EVENT_AFTER_TERMINAL,
                state = initialState,
                event = event,
                reason = "event arrived after terminal status ${initialState.terminalStatus}",
            )
            return ProcessingResult(state = initialState.updateLastEvent(event), outputs = outputs)
        }

        val brokerConflict = initialState.brokerOrderId != null &&
            event.brokerOrderId != null &&
            initialState.brokerOrderId != event.brokerOrderId
        if (brokerConflict) {
            outputs += anomaly(
                type = AnomalyType.CONFLICTING_BROKER_ORDER_ID,
                state = initialState,
                event = event,
                reason = "broker order id changed from ${initialState.brokerOrderId} to ${event.brokerOrderId}",
            )
        }

        return when (event.type) {
            OrderExecutionEventType.INTENT_CREATED -> {
                val state = initialState.mergeIntent(event)
                if (state.shouldEmitDeferredSubmittedMilestone()) {
                    val sourceEventIds = listOfNotNull(state.submittedEventId, event.eventId)
                    outputs += submittedMilestone(state, sourceEventIds, event.occurredAt)
                    ProcessingResult(
                        state = state.copy(submittedMilestoneEmitted = true),
                        outputs = outputs,
                    )
                } else {
                    ProcessingResult(
                        state = state,
                        outputs = outputs,
                    )
                }
            }

            OrderExecutionEventType.SUBMITTED -> {
                val state = initialState.markSubmitted(event)
                val shouldEmitMilestone = !initialState.submittedMilestoneEmitted && state.side != OrderSide.UNKNOWN
                if (shouldEmitMilestone) {
                    outputs += submittedMilestone(state, listOf(event.eventId), event.occurredAt)
                }
                ProcessingResult(
                    state = state.copy(
                        submittedMilestoneEmitted = initialState.submittedMilestoneEmitted || shouldEmitMilestone,
                    ),
                    outputs = outputs,
                    timeoutAtEpochMillis = event.occurredAt.plus(fillTimeout).toInstant().toEpochMilli(),
                )
            }

            OrderExecutionEventType.PARTIALLY_FILLED -> {
                if (!initialState.submitted) {
                    outputs += anomaly(
                        type = AnomalyType.FILL_BEFORE_SUBMIT,
                        state = initialState,
                        event = event,
                        reason = "partial fill arrived before submitted event",
                    )
                }
                val state = initialState.addFill(event, terminal = false)
                state.overfillAnomaly(event)?.let { outputs += it }
                outputs += fillMilestone(state, event, partial = true)
                ProcessingResult(state = state, outputs = outputs)
            }

            OrderExecutionEventType.FILLED -> {
                if (!initialState.submitted) {
                    outputs += anomaly(
                        type = AnomalyType.FILL_BEFORE_SUBMIT,
                        state = initialState,
                        event = event,
                        reason = "fill arrived before submitted event",
                    )
                }
                val state = initialState.addFill(event, terminal = true)
                state.overfillAnomaly(event)?.let { outputs += it }
                outputs += fillMilestone(state, event, partial = false)
                ProcessingResult(state = state, outputs = outputs)
            }

            OrderExecutionEventType.REJECTED -> {
                val state = initialState.markTerminal(event, OrderTerminalStatus.REJECTED)
                outputs += terminalMilestone(state, event, MilestoneType.ORDER_REJECTED)
                ProcessingResult(state = state, outputs = outputs)
            }

            OrderExecutionEventType.CANCELLED -> {
                val state = initialState.markTerminal(event, OrderTerminalStatus.CANCELLED)
                outputs += terminalMilestone(state, event, MilestoneType.ORDER_CANCELLED)
                ProcessingResult(state = state, outputs = outputs)
            }
        }
    }

    fun timeout(state: OrderLifecycleState, timeoutAt: ZonedDateTime): LifecycleEventEnvelope? {
        if (!state.submitted || state.hasTerminalStatus()) return null

        val timedOut = state.copy(
            terminalStatus = OrderTerminalStatus.TIMEOUT,
            terminalAt = timeoutAt,
            lastEventAt = timeoutAt,
        )
        return milestone(
            type = MilestoneType.ORDER_FILL_TIMEOUT_DETECTED,
            state = timedOut,
            sourceEventIds = listOf("timeout:${state.orderIntentId}:${timeoutAt.toInstant().toEpochMilli()}"),
            occurredAt = timeoutAt,
        )
    }

    private fun OrderExecutionEvent.initialState(): OrderLifecycleState {
        return OrderLifecycleState(
            orderIntentId = orderIntentId,
            strategyExecutionId = strategyExecutionId,
            orderTag = orderTag,
            strategyKind = strategyKind,
            side = side,
            expectedQuantity = expectedQuantity,
            brokerOrderId = brokerOrderId,
            lastEventAt = occurredAt,
        )
    }

    private fun OrderLifecycleState.mergeIntent(event: OrderExecutionEvent): OrderLifecycleState {
        return copy(
            orderTag = event.orderTag ?: orderTag,
            strategyKind = event.strategyKind.takeIf { it != StrategyExecutionKind.UNKNOWN } ?: strategyKind,
            side = event.side.takeIf { it != OrderSide.UNKNOWN } ?: side,
            expectedQuantity = event.expectedQuantity ?: expectedQuantity,
            lastEventAt = event.occurredAt,
        )
    }

    private fun OrderLifecycleState.markSubmitted(event: OrderExecutionEvent): OrderLifecycleState {
        return copy(
            submitted = true,
            submittedAt = submittedAt ?: event.occurredAt,
            submittedEventId = submittedEventId ?: event.eventId,
            brokerOrderId = event.brokerOrderId ?: brokerOrderId,
            lastEventAt = event.occurredAt,
        ).mergeIntent(event)
    }

    private fun OrderLifecycleState.addFill(
        event: OrderExecutionEvent,
        terminal: Boolean,
    ): OrderLifecycleState {
        val fillQuantity = checkNotNull(event.filledQuantity) { "filledQuantity is required for fill event" }
        val fillPrice = checkNotNull(event.filledPrice) { "filledPrice is required for fill event" }
        val nextFilledQuantity = filledQuantity + fillQuantity
        val nextAverage = weightedAverage(filledQuantity, averageFilledPrice, fillQuantity, fillPrice)

        return copy(
            brokerOrderId = event.brokerOrderId ?: brokerOrderId,
            filledQuantity = nextFilledQuantity,
            averageFilledPrice = nextAverage,
            terminalStatus = if (terminal) OrderTerminalStatus.FILLED else terminalStatus,
            terminalAt = if (terminal) event.occurredAt else terminalAt,
            lastEventAt = event.occurredAt,
        ).mergeIntent(event)
    }

    private fun OrderLifecycleState.markTerminal(
        event: OrderExecutionEvent,
        status: OrderTerminalStatus,
    ): OrderLifecycleState {
        return copy(
            brokerOrderId = event.brokerOrderId ?: brokerOrderId,
            terminalStatus = status,
            terminalAt = event.occurredAt,
            lastEventAt = event.occurredAt,
        ).mergeIntent(event)
    }

    private fun OrderLifecycleState.updateLastEvent(event: OrderExecutionEvent): OrderLifecycleState {
        return copy(lastEventAt = event.occurredAt)
    }

    private fun submittedMilestone(
        state: OrderLifecycleState,
        sourceEventIds: List<String>,
        occurredAt: ZonedDateTime,
    ): LifecycleEventEnvelope {
        return milestone(state.submittedMilestoneType(), state, sourceEventIds, occurredAt)
    }

    private fun fillMilestone(
        state: OrderLifecycleState,
        event: OrderExecutionEvent,
        partial: Boolean,
    ): LifecycleEventEnvelope {
        val type = if (partial) state.partialFillMilestoneType() else state.fullFillMilestoneType()
        return milestone(type, state, listOf(event.eventId), event.occurredAt)
    }

    private fun terminalMilestone(
        state: OrderLifecycleState,
        event: OrderExecutionEvent,
        type: MilestoneType,
    ): LifecycleEventEnvelope {
        return milestone(type, state, listOf(event.eventId), event.occurredAt)
    }

    private fun milestone(
        type: MilestoneType,
        state: OrderLifecycleState,
        sourceEventIds: List<String>,
        occurredAt: ZonedDateTime,
    ): LifecycleEventEnvelope {
        val seed = "laor-milestone:${state.strategyExecutionId}:${state.orderIntentId}:$type:${sourceEventIds.joinToString(",")}"
        return LifecycleEventEnvelope(
            milestone = MilestoneEvent(
                eventId = deterministicId(seed),
                milestoneType = type,
                strategyExecutionId = state.strategyExecutionId,
                orderIntentId = state.orderIntentId,
                brokerOrderId = state.brokerOrderId,
                orderTag = state.orderTag,
                side = state.side,
                filledQuantity = state.filledQuantity,
                averageFilledPrice = state.averageFilledPrice,
                occurredAt = occurredAt,
                sourceEventIds = sourceEventIds,
                idempotencyKey = "${state.strategyExecutionId}:${state.orderIntentId}:$type:${sourceEventIds.joinToString(",")}",
            ),
        )
    }

    private fun anomaly(
        type: AnomalyType,
        state: OrderLifecycleState,
        event: OrderExecutionEvent,
        reason: String,
    ): LifecycleEventEnvelope {
        val seed = "laor-anomaly:${event.strategyExecutionId}:${event.orderIntentId}:$type:${event.eventId}"
        return LifecycleEventEnvelope(
            anomaly = AnomalyEvent(
                eventId = deterministicId(seed),
                anomalyType = type,
                strategyExecutionId = event.strategyExecutionId,
                orderIntentId = event.orderIntentId,
                brokerOrderId = event.brokerOrderId ?: state.brokerOrderId,
                orderTag = event.orderTag ?: state.orderTag,
                side = event.side.takeIf { it != OrderSide.UNKNOWN } ?: state.side,
                reason = reason,
                occurredAt = event.occurredAt,
                sourceEventIds = listOf(event.eventId),
                idempotencyKey = "${event.strategyExecutionId}:${event.orderIntentId}:$type:${event.eventId}",
            ),
        )
    }

    private fun OrderLifecycleState.overfillAnomaly(event: OrderExecutionEvent): LifecycleEventEnvelope? {
        val expected = expectedQuantity ?: return null
        if (filledQuantity <= expected) return null
        return anomaly(
            type = AnomalyType.FILLED_QUANTITY_EXCEEDS_EXPECTED,
            state = this,
            event = event,
            reason = "filled quantity $filledQuantity exceeds expected quantity $expected",
        )
    }

    private fun OrderLifecycleState.submittedMilestoneType(): MilestoneType {
        return when (side) {
            OrderSide.BUY -> MilestoneType.ENTRY_BUY_SUBMITTED
            OrderSide.SELL -> MilestoneType.EXIT_SELL_SUBMITTED
            OrderSide.UNKNOWN -> error("cannot emit submitted milestone without order side")
        }
    }

    private fun OrderLifecycleState.partialFillMilestoneType(): MilestoneType {
        return when (side) {
            OrderSide.BUY -> MilestoneType.ENTRY_BUY_PARTIALLY_FILLED
            OrderSide.SELL -> MilestoneType.EXIT_SELL_PARTIALLY_FILLED
            OrderSide.UNKNOWN -> MilestoneType.ENTRY_BUY_PARTIALLY_FILLED
        }
    }

    private fun OrderLifecycleState.fullFillMilestoneType(): MilestoneType {
        return when (side) {
            OrderSide.BUY -> MilestoneType.ENTRY_BUY_FILLED
            OrderSide.SELL -> MilestoneType.EXIT_SELL_FILLED
            OrderSide.UNKNOWN -> MilestoneType.ENTRY_BUY_FILLED
        }
    }

    private fun weightedAverage(
        currentQuantity: Long,
        currentAverage: Double?,
        addedQuantity: Long,
        addedPrice: Double,
    ): Double {
        if (currentQuantity <= 0 || currentAverage == null) return addedPrice
        val totalQuantity = currentQuantity + addedQuantity
        return ((currentAverage * currentQuantity) + (addedPrice * addedQuantity)) / totalQuantity
    }

    companion object {
        const val DEFAULT_FILL_TIMEOUT_MINUTES = 30L
    }
}

private fun OrderExecutionEventType.isTerminalEvent(): Boolean {
    return this == OrderExecutionEventType.FILLED ||
        this == OrderExecutionEventType.REJECTED ||
        this == OrderExecutionEventType.CANCELLED
}

private fun OrderLifecycleState.shouldEmitDeferredSubmittedMilestone(): Boolean {
    return submitted && !submittedMilestoneEmitted && side != OrderSide.UNKNOWN
}

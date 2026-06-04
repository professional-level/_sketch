package com.example.streamprocessingservice.laor.application

import java.time.Duration
import java.time.ZonedDateTime

class LaorOrderLifecycleProcessor(
    private val fillTimeout: Duration = Duration.ofMinutes(DEFAULT_FILL_TIMEOUT_MINUTES),
) {
    fun process(
        event: LaorOrderExecutionEvent,
        current: OrderLifecycleState?,
    ): LaorProcessingResult {
        val initialState = current ?: event.initialState()
        val outputs = mutableListOf<LaorMilestoneEnvelope>()

        if (event.type == OrderExecutionEventType.INTENT_CREATED && event.strategyKind == StrategyExecutionKind.OTHER) {
            return LaorProcessingResult(state = initialState.mergeIntent(event), outputs = emptyList())
        }

        if (initialState.strategyKind == StrategyExecutionKind.OTHER) {
            return LaorProcessingResult(state = initialState.updateLastEvent(event), outputs = emptyList())
        }

        if (current == null && event.type != OrderExecutionEventType.INTENT_CREATED) {
            outputs += anomaly(
                type = LaorAnomalyType.UNKNOWN_ORDER_INTENT,
                state = initialState,
                event = event,
                reason = "order execution event arrived before order intent metadata",
            )
        }

        if (initialState.hasTerminalStatus() && event.type != OrderExecutionEventType.INTENT_CREATED) {
            if (event.type.isTerminalEvent()) {
                outputs += anomaly(
                    type = LaorAnomalyType.DUPLICATE_TERMINAL_EVENT,
                    state = initialState,
                    event = event,
                    reason = "terminal event arrived after terminal status ${initialState.terminalStatus}",
                )
            }
            outputs += anomaly(
                type = LaorAnomalyType.LATE_EVENT_AFTER_TERMINAL,
                state = initialState,
                event = event,
                reason = "event arrived after terminal status ${initialState.terminalStatus}",
            )
            return LaorProcessingResult(state = initialState.updateLastEvent(event), outputs = outputs)
        }

        val brokerConflict = initialState.brokerOrderId != null &&
            event.brokerOrderId != null &&
            initialState.brokerOrderId != event.brokerOrderId
        if (brokerConflict) {
            outputs += anomaly(
                type = LaorAnomalyType.CONFLICTING_BROKER_ORDER_ID,
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
                    LaorProcessingResult(
                        state = state.copy(submittedMilestoneEmitted = true),
                        outputs = outputs,
                    )
                } else {
                    LaorProcessingResult(
                        state = state,
                        outputs = outputs,
                    )
                }
            }

            OrderExecutionEventType.SUBMITTED -> {
                val state = initialState.markSubmitted(event)
                val shouldEmitMilestone = !initialState.submittedMilestoneEmitted && state.side != LaorOrderSide.UNKNOWN
                if (shouldEmitMilestone) {
                    outputs += submittedMilestone(state, listOf(event.eventId), event.occurredAt)
                }
                LaorProcessingResult(
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
                        type = LaorAnomalyType.FILL_BEFORE_SUBMIT,
                        state = initialState,
                        event = event,
                        reason = "partial fill arrived before submitted event",
                    )
                }
                val state = initialState.addFill(event, terminal = false)
                state.overfillAnomaly(event)?.let { outputs += it }
                outputs += fillMilestone(state, event, partial = true)
                LaorProcessingResult(state = state, outputs = outputs)
            }

            OrderExecutionEventType.FILLED -> {
                if (!initialState.submitted) {
                    outputs += anomaly(
                        type = LaorAnomalyType.FILL_BEFORE_SUBMIT,
                        state = initialState,
                        event = event,
                        reason = "fill arrived before submitted event",
                    )
                }
                val state = initialState.addFill(event, terminal = true)
                state.overfillAnomaly(event)?.let { outputs += it }
                outputs += fillMilestone(state, event, partial = false)
                LaorProcessingResult(state = state, outputs = outputs)
            }

            OrderExecutionEventType.REJECTED -> {
                val state = initialState.markTerminal(event, OrderTerminalStatus.REJECTED)
                outputs += terminalMilestone(state, event, LaorMilestoneType.ORDER_REJECTED)
                LaorProcessingResult(state = state, outputs = outputs)
            }

            OrderExecutionEventType.CANCELLED -> {
                val state = initialState.markTerminal(event, OrderTerminalStatus.CANCELLED)
                outputs += terminalMilestone(state, event, LaorMilestoneType.ORDER_CANCELLED)
                LaorProcessingResult(state = state, outputs = outputs)
            }
        }
    }

    fun timeout(state: OrderLifecycleState, timeoutAt: ZonedDateTime): LaorMilestoneEnvelope? {
        if (!state.submitted || state.hasTerminalStatus()) return null

        val timedOut = state.copy(
            terminalStatus = OrderTerminalStatus.TIMEOUT,
            terminalAt = timeoutAt,
            lastEventAt = timeoutAt,
        )
        return milestone(
            type = LaorMilestoneType.ORDER_FILL_TIMEOUT_DETECTED,
            state = timedOut,
            sourceEventIds = listOf("timeout:${state.orderIntentId}:${timeoutAt.toInstant().toEpochMilli()}"),
            occurredAt = timeoutAt,
        )
    }

    private fun LaorOrderExecutionEvent.initialState(): OrderLifecycleState {
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

    private fun OrderLifecycleState.mergeIntent(event: LaorOrderExecutionEvent): OrderLifecycleState {
        return copy(
            orderTag = event.orderTag ?: orderTag,
            strategyKind = event.strategyKind.takeIf { it != StrategyExecutionKind.UNKNOWN } ?: strategyKind,
            side = event.side.takeIf { it != LaorOrderSide.UNKNOWN } ?: side,
            expectedQuantity = event.expectedQuantity ?: expectedQuantity,
            lastEventAt = event.occurredAt,
        )
    }

    private fun OrderLifecycleState.markSubmitted(event: LaorOrderExecutionEvent): OrderLifecycleState {
        return copy(
            submitted = true,
            submittedAt = submittedAt ?: event.occurredAt,
            submittedEventId = submittedEventId ?: event.eventId,
            brokerOrderId = event.brokerOrderId ?: brokerOrderId,
            lastEventAt = event.occurredAt,
        ).mergeIntent(event)
    }

    private fun OrderLifecycleState.addFill(
        event: LaorOrderExecutionEvent,
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
        event: LaorOrderExecutionEvent,
        status: OrderTerminalStatus,
    ): OrderLifecycleState {
        return copy(
            brokerOrderId = event.brokerOrderId ?: brokerOrderId,
            terminalStatus = status,
            terminalAt = event.occurredAt,
            lastEventAt = event.occurredAt,
        ).mergeIntent(event)
    }

    private fun OrderLifecycleState.updateLastEvent(event: LaorOrderExecutionEvent): OrderLifecycleState {
        return copy(lastEventAt = event.occurredAt)
    }

    private fun submittedMilestone(
        state: OrderLifecycleState,
        sourceEventIds: List<String>,
        occurredAt: ZonedDateTime,
    ): LaorMilestoneEnvelope {
        return milestone(state.submittedMilestoneType(), state, sourceEventIds, occurredAt)
    }

    private fun fillMilestone(
        state: OrderLifecycleState,
        event: LaorOrderExecutionEvent,
        partial: Boolean,
    ): LaorMilestoneEnvelope {
        val type = if (partial) state.partialFillMilestoneType() else state.fullFillMilestoneType()
        return milestone(type, state, listOf(event.eventId), event.occurredAt)
    }

    private fun terminalMilestone(
        state: OrderLifecycleState,
        event: LaorOrderExecutionEvent,
        type: LaorMilestoneType,
    ): LaorMilestoneEnvelope {
        return milestone(type, state, listOf(event.eventId), event.occurredAt)
    }

    private fun milestone(
        type: LaorMilestoneType,
        state: OrderLifecycleState,
        sourceEventIds: List<String>,
        occurredAt: ZonedDateTime,
    ): LaorMilestoneEnvelope {
        val seed = "laor-milestone:${state.strategyExecutionId}:${state.orderIntentId}:$type:${sourceEventIds.joinToString(",")}"
        return LaorMilestoneEnvelope(
            milestone = LaorMilestoneEvent(
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
        type: LaorAnomalyType,
        state: OrderLifecycleState,
        event: LaorOrderExecutionEvent,
        reason: String,
    ): LaorMilestoneEnvelope {
        val seed = "laor-anomaly:${event.strategyExecutionId}:${event.orderIntentId}:$type:${event.eventId}"
        return LaorMilestoneEnvelope(
            anomaly = LaorAnomalyEvent(
                eventId = deterministicId(seed),
                anomalyType = type,
                strategyExecutionId = event.strategyExecutionId,
                orderIntentId = event.orderIntentId,
                brokerOrderId = event.brokerOrderId ?: state.brokerOrderId,
                orderTag = event.orderTag ?: state.orderTag,
                side = event.side.takeIf { it != LaorOrderSide.UNKNOWN } ?: state.side,
                reason = reason,
                occurredAt = event.occurredAt,
                sourceEventIds = listOf(event.eventId),
                idempotencyKey = "${event.strategyExecutionId}:${event.orderIntentId}:$type:${event.eventId}",
            ),
        )
    }

    private fun OrderLifecycleState.overfillAnomaly(event: LaorOrderExecutionEvent): LaorMilestoneEnvelope? {
        val expected = expectedQuantity ?: return null
        if (filledQuantity <= expected) return null
        return anomaly(
            type = LaorAnomalyType.FILLED_QUANTITY_EXCEEDS_EXPECTED,
            state = this,
            event = event,
            reason = "filled quantity $filledQuantity exceeds expected quantity $expected",
        )
    }

    private fun OrderLifecycleState.submittedMilestoneType(): LaorMilestoneType {
        return when (side) {
            LaorOrderSide.BUY -> LaorMilestoneType.ENTRY_BUY_SUBMITTED
            LaorOrderSide.SELL -> LaorMilestoneType.EXIT_SELL_SUBMITTED
            LaorOrderSide.UNKNOWN -> error("cannot emit submitted milestone without order side")
        }
    }

    private fun OrderLifecycleState.partialFillMilestoneType(): LaorMilestoneType {
        return when (side) {
            LaorOrderSide.BUY -> LaorMilestoneType.ENTRY_BUY_PARTIALLY_FILLED
            LaorOrderSide.SELL -> LaorMilestoneType.EXIT_SELL_PARTIALLY_FILLED
            LaorOrderSide.UNKNOWN -> LaorMilestoneType.ENTRY_BUY_PARTIALLY_FILLED
        }
    }

    private fun OrderLifecycleState.fullFillMilestoneType(): LaorMilestoneType {
        return when (side) {
            LaorOrderSide.BUY -> LaorMilestoneType.ENTRY_BUY_FILLED
            LaorOrderSide.SELL -> LaorMilestoneType.EXIT_SELL_FILLED
            LaorOrderSide.UNKNOWN -> LaorMilestoneType.ENTRY_BUY_FILLED
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
    return submitted && !submittedMilestoneEmitted && side != LaorOrderSide.UNKNOWN
}

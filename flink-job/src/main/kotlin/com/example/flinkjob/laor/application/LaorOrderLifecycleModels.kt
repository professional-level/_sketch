package com.example.flinkjob.laor.application

import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.util.UUID

enum class OrderExecutionEventType {
    INTENT_CREATED,
    SUBMITTED,
    PARTIALLY_FILLED,
    FILLED,
    REJECTED,
    CANCELLED,
}

enum class LaorOrderSide {
    BUY,
    SELL,
    UNKNOWN,
}

enum class OrderTerminalStatus {
    FILLED,
    REJECTED,
    CANCELLED,
    TIMEOUT,
}

enum class StrategyExecutionKind {
    LAOR_V4,
    OTHER,
    UNKNOWN,
}

enum class LaorMilestoneType {
    ENTRY_BUY_SUBMITTED,
    ENTRY_BUY_PARTIALLY_FILLED,
    ENTRY_BUY_FILLED,
    EXIT_SELL_SUBMITTED,
    EXIT_SELL_PARTIALLY_FILLED,
    EXIT_SELL_FILLED,
    ORDER_REJECTED,
    ORDER_CANCELLED,
    ORDER_FILL_TIMEOUT_DETECTED,
}

enum class LaorAnomalyType {
    FILL_BEFORE_SUBMIT,
    DUPLICATE_TERMINAL_EVENT,
    FILLED_QUANTITY_EXCEEDS_EXPECTED,
    CONFLICTING_BROKER_ORDER_ID,
    UNKNOWN_ORDER_INTENT,
    LATE_EVENT_AFTER_TERMINAL,
}

data class LaorOrderExecutionEvent(
    val eventId: String,
    val type: OrderExecutionEventType,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String? = null,
    val strategyKind: StrategyExecutionKind = StrategyExecutionKind.UNKNOWN,
    val side: LaorOrderSide = LaorOrderSide.UNKNOWN,
    val orderTag: String? = null,
    val expectedQuantity: Long? = null,
    val filledPrice: Double? = null,
    val filledQuantity: Long? = null,
    val reason: String? = null,
    val occurredAt: ZonedDateTime,
) : Serializable {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(strategyExecutionId.isNotBlank()) { "strategyExecutionId must not be blank" }
        require(orderIntentId.isNotBlank()) { "orderIntentId must not be blank" }
        brokerOrderId?.let { require(it.isNotBlank()) { "brokerOrderId must not be blank" } }
        expectedQuantity?.let { require(it > 0) { "expectedQuantity must be positive" } }
        filledPrice?.let { require(it > 0.0) { "filledPrice must be positive" } }
        filledQuantity?.let { require(it > 0) { "filledQuantity must be positive" } }
    }
}

data class OrderLifecycleState(
    val orderIntentId: String = "",
    val strategyExecutionId: String = "",
    val orderTag: String? = null,
    val strategyKind: StrategyExecutionKind = StrategyExecutionKind.UNKNOWN,
    val side: LaorOrderSide = LaorOrderSide.UNKNOWN,
    val expectedQuantity: Long? = null,
    val submitted: Boolean = false,
    val submittedAt: ZonedDateTime? = null,
    val submittedEventId: String? = null,
    val submittedMilestoneEmitted: Boolean = false,
    val brokerOrderId: String? = null,
    val filledQuantity: Long = 0,
    val averageFilledPrice: Double? = null,
    val lastEventAt: ZonedDateTime? = null,
    val terminalStatus: OrderTerminalStatus? = null,
    val terminalAt: ZonedDateTime? = null,
) : Serializable {
    fun hasTerminalStatus(): Boolean = terminalStatus != null
}

data class LaorMilestoneEvent(
    val eventId: String,
    val milestoneType: LaorMilestoneType,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String?,
    val orderTag: String?,
    val side: LaorOrderSide,
    val filledQuantity: Long,
    val averageFilledPrice: Double?,
    val occurredAt: ZonedDateTime,
    val sourceEventIds: List<String>,
    val idempotencyKey: String,
) : Serializable

data class LaorAnomalyEvent(
    val eventId: String,
    val anomalyType: LaorAnomalyType,
    val strategyExecutionId: String,
    val orderIntentId: String,
    val brokerOrderId: String?,
    val orderTag: String?,
    val side: LaorOrderSide,
    val reason: String,
    val occurredAt: ZonedDateTime,
    val sourceEventIds: List<String>,
    val idempotencyKey: String,
) : Serializable

data class LaorMilestoneEnvelope(
    val milestone: LaorMilestoneEvent? = null,
    val anomaly: LaorAnomalyEvent? = null,
) : Serializable {
    init {
        require((milestone == null) xor (anomaly == null)) {
            "exactly one of milestone or anomaly must be present"
        }
    }

    val strategyExecutionId: String
        get() = milestone?.strategyExecutionId ?: checkNotNull(anomaly).strategyExecutionId

    val isAnomaly: Boolean
        get() = anomaly != null
}

data class LaorProcessingResult(
    val state: OrderLifecycleState,
    val outputs: List<LaorMilestoneEnvelope> = emptyList(),
    val timeoutAtEpochMillis: Long? = null,
) : Serializable

internal fun deterministicId(seed: String): String {
    return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
}

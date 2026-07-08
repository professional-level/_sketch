package com.example.strategyexecutionservice.application.port.`in`

import com.example.common.UseCase
import com.example.strategyexecutionservice.domain.strategy.execution.OrderSide
import java.time.ZonedDateTime

@UseCase
interface RecordOrderExecutionEventUseCase {
    suspend fun execute(command: RecordOrderExecutionEventCommand): RecordOrderExecutionEventResult
}

sealed class RecordOrderExecutionEventCommand {
    abstract val eventId: String
    abstract val strategyExecutionId: String
    abstract val orderIntentId: String
    abstract val brokerOrderId: String?
    abstract val occurredAt: ZonedDateTime
    abstract val idempotencyKey: String

    data class Submitted(
        override val eventId: String,
        override val strategyExecutionId: String,
        override val orderIntentId: String,
        override val brokerOrderId: String,
        val side: OrderSide? = null,
        val orderTag: String? = null,
        val quantity: Long? = null,
        val submittedAt: ZonedDateTime,
        override val idempotencyKey: String = eventId,
    ) : RecordOrderExecutionEventCommand() {
        override val occurredAt: ZonedDateTime = submittedAt

        init {
            require(eventId.isNotBlank()) { "eventId must not be blank" }
            require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
            require(strategyExecutionId.isNotBlank()) { "strategyExecutionId must not be blank" }
            require(orderIntentId.isNotBlank()) { "orderIntentId must not be blank" }
            require(brokerOrderId.isNotBlank()) { "brokerOrderId must not be blank" }
            orderTag?.let { require(it.isNotBlank()) { "orderTag must not be blank" } }
            quantity?.let { require(it > 0) { "quantity must be positive" } }
        }
    }

    data class Rejected(
        override val eventId: String,
        override val strategyExecutionId: String,
        override val orderIntentId: String,
        override val brokerOrderId: String?,
        val reason: String,
        val rejectedAt: ZonedDateTime,
        override val idempotencyKey: String = eventId,
    ) : RecordOrderExecutionEventCommand() {
        override val occurredAt: ZonedDateTime = rejectedAt

        init {
            require(eventId.isNotBlank()) { "eventId must not be blank" }
            require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
            require(strategyExecutionId.isNotBlank()) { "strategyExecutionId must not be blank" }
            require(orderIntentId.isNotBlank()) { "orderIntentId must not be blank" }
            brokerOrderId?.let { require(it.isNotBlank()) { "brokerOrderId must not be blank" } }
            require(reason.isNotBlank()) { "reason must not be blank" }
        }
    }

    data class Cancelled(
        override val eventId: String,
        override val strategyExecutionId: String,
        override val orderIntentId: String,
        override val brokerOrderId: String,
        val reason: String,
        val cancelledAt: ZonedDateTime,
        override val idempotencyKey: String = eventId,
    ) : RecordOrderExecutionEventCommand() {
        override val occurredAt: ZonedDateTime = cancelledAt

        init {
            require(eventId.isNotBlank()) { "eventId must not be blank" }
            require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
            require(strategyExecutionId.isNotBlank()) { "strategyExecutionId must not be blank" }
            require(orderIntentId.isNotBlank()) { "orderIntentId must not be blank" }
            require(brokerOrderId.isNotBlank()) { "brokerOrderId must not be blank" }
            require(reason.isNotBlank()) { "reason must not be blank" }
        }
    }
}

data class RecordOrderExecutionEventResult(
    val strategyExecutionId: String,
    val status: RecordOrderExecutionEventStatus,
)

enum class RecordOrderExecutionEventStatus {
    RECORDED,
    SKIPPED_DUPLICATE,
}

package com.example.stockpurchaseservice.application.service

import com.example.common.UseCaseImpl
import com.example.stockpurchaseservice.application.port.`in`.RecoverUnknownOrderSubmissionsUseCase
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OrderCancelledMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionEventPort
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.SubmissionUnknownAlert
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.ZonedDateTime
import java.util.UUID

@UseCaseImpl
class RecoverUnknownOrderSubmissionsService(
    private val marketService: MarketServicePort,
    private val orderIntentSubmissionPort: OrderIntentSubmissionPort,
    private val orderExecutionEventPort: OrderExecutionEventPort,
    private val operationalAlertPort: OperationalAlertPort,
) : RecoverUnknownOrderSubmissionsUseCase {
    internal var clock: Clock = Clock.systemDefaultZone()

    override suspend fun execute() {
        orderIntentSubmissionPort.findUnknownSubmissions().forEach { submission ->
            recover(submission)
        }
        orderIntentSubmissionPort.findCancelPendingSubmissions().forEach { submission ->
            recoverCancelPending(submission)
        }
    }

    private suspend fun recover(submission: OrderIntentSubmissionDto) {
        val status = lookupStatus(submission, RecoveryMode.UNKNOWN_SUBMISSION) ?: return
        when (status.status) {
            BrokerOrderStatus.SUBMITTED,
            BrokerOrderStatus.PARTIALLY_FILLED,
            BrokerOrderStatus.FILLED -> markSubmitted(submission, status)
            BrokerOrderStatus.REJECTED -> markRejected(submission, status)
            BrokerOrderStatus.CANCELLED -> markCancelled(submission, status)
            BrokerOrderStatus.UNKNOWN -> {
                val unresolved = submission.copy(
                    statusReason = status.reason,
                    lastStatusCheckedAt = status.checkedAt,
                )
                orderIntentSubmissionPort.saveUnknown(unresolved)
                runCatching {
                    operationalAlertPort.alertSubmissionUnknown(unresolved.toSubmissionUnknownAlert(status))
                }
            }
        }
    }

    private suspend fun recoverCancelPending(submission: OrderIntentSubmissionDto) {
        val status = lookupStatus(submission, RecoveryMode.CANCEL_PENDING) ?: return
        when (status.status) {
            BrokerOrderStatus.CANCELLED -> markCancelled(submission, status)
            BrokerOrderStatus.REJECTED -> markRejected(submission, status)
            BrokerOrderStatus.FILLED -> markSubmittedWithoutRepublishing(submission, status)
            BrokerOrderStatus.SUBMITTED,
            BrokerOrderStatus.PARTIALLY_FILLED,
            BrokerOrderStatus.UNKNOWN -> {
                val pending = submission.copy(
                    statusReason = status.reason ?: submission.statusReason,
                    lastStatusCheckedAt = status.checkedAt,
                )
                orderIntentSubmissionPort.saveCancelPending(pending)
                if (status.status == BrokerOrderStatus.UNKNOWN) {
                    runCatching {
                        operationalAlertPort.alertSubmissionUnknown(pending.toSubmissionUnknownAlert(status))
                    }
                }
            }
        }
    }

    private suspend fun lookupStatus(
        submission: OrderIntentSubmissionDto,
        mode: RecoveryMode,
    ): BrokerOrderStatusDto? {
        return try {
            marketService.findOrderSubmissionStatus(submission.toQuery())
        } catch (exception: RuntimeException) {
            markRecoveryLookupFailed(submission, mode, exception)
            null
        }
    }

    private suspend fun markRecoveryLookupFailed(
        submission: OrderIntentSubmissionDto,
        mode: RecoveryMode,
        exception: RuntimeException,
    ) {
        val checkedAt = ZonedDateTime.now(clock)
        val unresolved = submission.copy(
            statusReason = "broker status lookup failed: ${exception.message ?: exception::class.java.simpleName}",
            lastStatusCheckedAt = checkedAt,
        )
        when (mode) {
            RecoveryMode.UNKNOWN_SUBMISSION -> orderIntentSubmissionPort.saveUnknown(unresolved)
            RecoveryMode.CANCEL_PENDING -> orderIntentSubmissionPort.saveCancelPending(unresolved)
        }
        runCatching {
            operationalAlertPort.alertSubmissionUnknown(
                unresolved.toSubmissionUnknownAlert(
                    BrokerOrderStatusDto(
                        status = BrokerOrderStatus.UNKNOWN,
                        externalOrderId = unresolved.externalOrderId,
                        reason = unresolved.statusReason,
                        checkedAt = checkedAt,
                    ),
                ),
            )
        }
    }

    private suspend fun markSubmitted(
        submission: OrderIntentSubmissionDto,
        status: BrokerOrderStatusDto,
    ) {
        val externalOrderId = status.externalOrderId ?: submission.externalOrderId ?: return
        val recovered = submission.copy(
            externalOrderId = externalOrderId,
            statusReason = null,
            lastStatusCheckedAt = status.checkedAt,
        )
        orderIntentSubmissionPort.saveSubmitted(recovered)
        orderExecutionEventPort.publishSubmitted(recovered.toSubmittedMessage())
    }

    private suspend fun markSubmittedWithoutRepublishing(
        submission: OrderIntentSubmissionDto,
        status: BrokerOrderStatusDto,
    ) {
        val externalOrderId = status.externalOrderId ?: submission.externalOrderId ?: return
        val recovered = submission.copy(
            externalOrderId = externalOrderId,
            statusReason = status.reason,
            lastStatusCheckedAt = status.checkedAt,
        )
        orderIntentSubmissionPort.saveSubmitted(recovered)
    }

    private suspend fun markRejected(
        submission: OrderIntentSubmissionDto,
        status: BrokerOrderStatusDto,
    ) {
        val recovered = submission.copy(
            statusReason = status.reason,
            lastStatusCheckedAt = status.checkedAt,
        )
        orderIntentSubmissionPort.saveRejected(recovered)
        orderExecutionEventPort.publishRejected(recovered.toRejectedMessage(status))
    }

    private suspend fun markCancelled(
        submission: OrderIntentSubmissionDto,
        status: BrokerOrderStatusDto,
    ) {
        val externalOrderId = status.externalOrderId ?: submission.externalOrderId ?: return
        val recovered = submission.copy(
            externalOrderId = externalOrderId,
            statusReason = status.reason,
            lastStatusCheckedAt = status.checkedAt,
        )
        orderIntentSubmissionPort.saveCancelled(recovered)
        orderExecutionEventPort.publishCancelled(recovered.toCancelledMessage(status, externalOrderId))
    }

    private fun OrderIntentSubmissionDto.toQuery(): BrokerOrderStatusQuery {
        return BrokerOrderStatusQuery(
            orderIntentId = orderIntentId,
            internalOrderId = internalOrderId,
            externalOrderId = externalOrderId,
            symbol = symbol,
            exchange = exchange,
            side = side,
            orderedQuantity = quantity,
            submittedPrice = submittedPrice,
            market = market ?: symbol.toStockOrderMarket(),
            submittedAt = submittedAt,
        )
    }

    private fun OrderIntentSubmissionDto.toSubmittedMessage(): OrderSubmittedMessage {
        return OrderSubmittedMessage(
            eventId = deterministicEventId("${orderIntentId}:SUBMITTED"),
            strategyExecutionId = strategyExecutionId,
            orderIntentId = orderIntentId.toString(),
            brokerOrderId = checkNotNull(externalOrderId),
            submittedAt = lastStatusCheckedAt ?: ZonedDateTime.now(),
        )
    }

    private fun OrderIntentSubmissionDto.toRejectedMessage(
        status: BrokerOrderStatusDto,
    ): OrderRejectedMessage {
        return OrderRejectedMessage(
            eventId = deterministicEventId("${orderIntentId}:REJECTED"),
            strategyExecutionId = strategyExecutionId,
            orderIntentId = orderIntentId.toString(),
            brokerOrderId = status.externalOrderId ?: externalOrderId,
            reason = status.reason ?: "broker order rejected",
            rejectedAt = status.checkedAt,
        )
    }

    private fun OrderIntentSubmissionDto.toCancelledMessage(
        status: BrokerOrderStatusDto,
        externalOrderId: String,
    ): OrderCancelledMessage {
        return OrderCancelledMessage(
            eventId = deterministicEventId("${orderIntentId}:CANCELLED"),
            strategyExecutionId = strategyExecutionId,
            orderIntentId = orderIntentId.toString(),
            brokerOrderId = externalOrderId,
            reason = status.reason ?: "broker order cancelled",
            cancelledAt = status.checkedAt,
        )
    }

    private fun OrderIntentSubmissionDto.toSubmissionUnknownAlert(
        status: BrokerOrderStatusDto,
    ): SubmissionUnknownAlert {
        return SubmissionUnknownAlert(
            orderIntentId = orderIntentId,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = strategyExecutionId,
            symbol = symbol,
            side = side,
            orderType = orderType,
            orderTag = orderTag,
            externalOrderId = status.externalOrderId ?: externalOrderId,
            reason = status.reason,
            submittedAt = submittedAt,
            checkedAt = status.checkedAt,
        )
    }

    private fun deterministicEventId(seed: String): UUID {
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8))
    }

    private fun String.toStockOrderMarket(): StockOrderMarket {
        return when {
            length == 6 && all(Char::isDigit) -> StockOrderMarket.DOMESTIC
            else -> StockOrderMarket.OVERSEAS_US
        }
    }

    private enum class RecoveryMode {
        UNKNOWN_SUBMISSION,
        CANCEL_PENDING,
    }
}

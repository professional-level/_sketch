package com.example.stockpurchaseservice.application.service

import com.example.common.UseCaseImpl
import com.example.stockpurchaseservice.application.port.`in`.RecoverUnknownOrderSubmissionsUseCase
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.ExecutionFillDto
import com.example.stockpurchaseservice.application.port.out.ExecutionFillPort
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OrderCancelledMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionEventPort
import com.example.stockpurchaseservice.application.port.out.OrderFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.OrderPartiallyFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.SubmissionUnknownAlert
import com.example.stockpurchaseservice.application.port.out.StockOrderPort
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.repository.OrderStateDto
import org.springframework.beans.factory.annotation.Value
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID

@UseCaseImpl
class RecoverUnknownOrderSubmissionsService(
    private val marketService: MarketServicePort,
    private val orderIntentSubmissionPort: OrderIntentSubmissionPort,
    private val orderExecutionEventPort: OrderExecutionEventPort,
    private val operationalAlertPort: OperationalAlertPort,
    private val stockOrderPort: StockOrderPort? = null,
    private val executionFillPort: ExecutionFillPort? = null,
    @Value("\${akra.operations.trading.persistent-submission-unknown-threshold:15m}")
    private val persistentSubmissionUnknownThreshold: Duration = Duration.ofMinutes(15),
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
            BrokerOrderStatus.SUBMITTED -> markSubmitted(submission, status)
            BrokerOrderStatus.PARTIALLY_FILLED,
            BrokerOrderStatus.FILLED -> markSubmitted(submission, status)?.let { recovered ->
                recoverFillFromStatus(recovered, status)
            }
            BrokerOrderStatus.REJECTED -> markRejected(submission, status)
            BrokerOrderStatus.CANCELLED -> {
                recoverFillFromStatus(submission, status)
                markCancelled(submission, status)
            }
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
            BrokerOrderStatus.CANCELLED -> {
                recoverFillFromStatus(submission, status)
                markCancelled(submission, status)
            }
            BrokerOrderStatus.REJECTED -> markRejected(submission, status)
            BrokerOrderStatus.FILLED -> markSubmittedWithoutRepublishing(submission, status)?.let { recovered ->
                recoverFillFromStatus(recovered, status)
            }
            BrokerOrderStatus.PARTIALLY_FILLED -> {
                val pending = submission.copy(
                    externalOrderId = status.externalOrderId ?: submission.externalOrderId,
                    statusReason = status.reason ?: submission.statusReason,
                    lastStatusCheckedAt = status.checkedAt,
                )
                orderIntentSubmissionPort.saveCancelPending(pending)
                recoverFillFromStatus(pending, status)
            }
            BrokerOrderStatus.SUBMITTED,
            BrokerOrderStatus.UNKNOWN -> {
                val pending = submission.copy(
                    externalOrderId = status.externalOrderId ?: submission.externalOrderId,
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
    ): OrderIntentSubmissionDto? {
        val externalOrderId = status.externalOrderId ?: submission.externalOrderId ?: return null
        val recovered = submission.copy(
            externalOrderId = externalOrderId,
            statusReason = null,
            lastStatusCheckedAt = status.checkedAt,
        )
        orderIntentSubmissionPort.saveSubmitted(recovered)
        syncRecoveredLegacySellOrder(recovered, externalOrderId, OrderStateDto.SELLING_IN_PROCESS)
        orderExecutionEventPort.publishSubmitted(recovered.toSubmittedMessage())
        return recovered
    }

    private suspend fun markSubmittedWithoutRepublishing(
        submission: OrderIntentSubmissionDto,
        status: BrokerOrderStatusDto,
    ): OrderIntentSubmissionDto? {
        val externalOrderId = status.externalOrderId ?: submission.externalOrderId ?: return null
        val recovered = submission.copy(
            externalOrderId = externalOrderId,
            statusReason = status.reason,
            lastStatusCheckedAt = status.checkedAt,
        )
        orderIntentSubmissionPort.saveSubmitted(recovered)
        return recovered
    }

    private suspend fun recoverFillFromStatus(
        submission: OrderIntentSubmissionDto,
        status: BrokerOrderStatusDto,
    ) {
        val fillPort = executionFillPort ?: return
        val externalOrderId = status.externalOrderId ?: submission.externalOrderId ?: return
        val cumulativeFilledQuantity = status.cumulativeFilledQuantity?.takeIf { it > 0 } ?: return
        val alreadySavedQuantity = fillPort.sumQuantityByExternalOrderId(externalOrderId)
        val deltaFilledQuantity = cumulativeFilledQuantity - alreadySavedQuantity
        if (deltaFilledQuantity <= 0 || deltaFilledQuantity > Int.MAX_VALUE.toLong()) return

        val executionType = submission.side.toExecutionType()
        val externalExecutionId = status.externalExecutionId
            ?: submission.fallbackExternalExecutionId(
                externalOrderId = externalOrderId,
                cumulativeFilledQuantity = cumulativeFilledQuantity,
                executionType = executionType,
            )
        val fullyFilled = status.status == BrokerOrderStatus.FILLED ||
            cumulativeFilledQuantity >= submission.quantity
        if (fillPort.exists(externalExecutionId)) {
            syncRecoveredLegacyFillCompletion(submission, externalOrderId, fullyFilled)
            return
        }

        val filledPrice = status.averageExecutionPrice ?: submission.submittedPrice ?: return
        val filledAt = status.brokerReportedAt ?: status.checkedAt
        val fill = ExecutionFillDto(
            externalExecutionId = externalExecutionId,
            externalOrderId = externalOrderId,
            stockId = submission.symbol,
            stockName = submission.symbol,
            createdAt = filledAt,
            quantity = deltaFilledQuantity.toInt(),
            type = executionType,
        )

        publishRecoveredFillEvent(
            submission = submission,
            externalOrderId = externalOrderId,
            externalExecutionId = externalExecutionId,
            filledPrice = filledPrice,
            filledQuantity = deltaFilledQuantity,
            filledAt = filledAt,
            fullyFilled = fullyFilled,
        )
        fillPort.saveIfNew(fill)
        syncRecoveredLegacyFillCompletion(submission, externalOrderId, fullyFilled)
    }

    private suspend fun syncRecoveredLegacyFillCompletion(
        submission: OrderIntentSubmissionDto,
        externalOrderId: String,
        fullyFilled: Boolean,
    ) {
        if (!fullyFilled || submission.side != OrderIntentSide.SELL) return
        syncRecoveredLegacySellOrder(submission, externalOrderId, OrderStateDto.SELLING_COMPLETED)
    }

    private suspend fun publishRecoveredFillEvent(
        submission: OrderIntentSubmissionDto,
        externalOrderId: String,
        externalExecutionId: String,
        filledPrice: Double,
        filledQuantity: Long,
        filledAt: ZonedDateTime,
        fullyFilled: Boolean,
    ) {
        if (fullyFilled) {
            orderExecutionEventPort.publishFilled(
                OrderFilledMessage(
                    eventId = deterministicEventId("$externalExecutionId:ORDER_FILLED"),
                    strategyExecutionId = submission.strategyExecutionId,
                    orderIntentId = submission.orderIntentId.toString(),
                    brokerOrderId = externalOrderId,
                    side = submission.side,
                    filledPrice = filledPrice,
                    filledQuantity = filledQuantity,
                    orderTag = submission.orderTag,
                    filledAt = filledAt,
                ),
            )
        } else {
            orderExecutionEventPort.publishPartiallyFilled(
                OrderPartiallyFilledMessage(
                    eventId = deterministicEventId("$externalExecutionId:ORDER_PARTIALLY_FILLED"),
                    strategyExecutionId = submission.strategyExecutionId,
                    orderIntentId = submission.orderIntentId.toString(),
                    brokerOrderId = externalOrderId,
                    side = submission.side,
                    filledPrice = filledPrice,
                    filledQuantity = filledQuantity,
                    orderTag = submission.orderTag,
                    filledAt = filledAt,
                ),
            )
        }
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
        syncRecoveredLegacySellOrder(
            submission = recovered,
            externalOrderId = status.externalOrderId ?: submission.externalOrderId,
            targetState = OrderStateDto.SUBMIT_FAILED,
        )
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
        syncRecoveredLegacySellOrder(recovered, externalOrderId, OrderStateDto.SUBMIT_FAILED)
        orderExecutionEventPort.publishCancelled(recovered.toCancelledMessage(status, externalOrderId))
    }

    private suspend fun syncRecoveredLegacySellOrder(
        submission: OrderIntentSubmissionDto,
        externalOrderId: String?,
        targetState: OrderStateDto,
    ) {
        val port = stockOrderPort ?: return
        val legacyOrderId = submission.legacySellOrderId() ?: return
        val order = port.findById(legacyOrderId) ?: return
        val nextState = order.orderState.nextRecoveredLegacySellState(targetState)
        if (nextState != null && nextState != order.orderState) {
            port.save(order.copy(orderState = nextState))
        }
        if (!externalOrderId.isNullOrBlank() && port.findByExternalOrderId(externalOrderId) == null) {
            runCatching { port.saveExternalOrderId(legacyOrderId, externalOrderId) }
        }
    }

    private fun OrderIntentSubmissionDto.toQuery(): BrokerOrderStatusQuery {
        return BrokerOrderStatusQuery(
            orderIntentId = orderIntentId,
            internalOrderId = internalOrderId,
            externalOrderId = externalOrderId,
            branchOrderNumber = branchOrderNumber,
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
        val ageSeconds = Duration.between(submittedAt, status.checkedAt).seconds.coerceAtLeast(0)
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
            ageSeconds = ageSeconds,
            persistent = ageSeconds >= persistentSubmissionUnknownThreshold.seconds,
        )
    }

    private fun OrderIntentSubmissionDto.fallbackExternalExecutionId(
        externalOrderId: String,
        cumulativeFilledQuantity: Long,
        executionType: ExecutionTypeDto,
    ): String {
        val fallback = "$externalOrderId:$cumulativeFilledQuantity:$executionType"
        val marketPrefix = "${(market ?: symbol.toStockOrderMarket()).name}:"
        return if (fallback.startsWith(marketPrefix)) fallback else "$marketPrefix$fallback"
    }

    private fun deterministicEventId(seed: String): UUID {
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8))
    }

    private fun OrderIntentSubmissionDto.legacySellOrderId(): UUID? {
        if (!idempotencyKey.startsWith("legacy-sell:")) return null
        return idempotencyKey
            .split(":")
            .asSequence()
            .drop(1)
            .mapNotNull { token -> runCatching { UUID.fromString(token) }.getOrNull() }
            .firstOrNull()
    }

    private fun OrderStateDto.nextRecoveredLegacySellState(targetState: OrderStateDto): OrderStateDto? {
        return when (targetState) {
            OrderStateDto.SELLING_IN_PROCESS -> when (this) {
                OrderStateDto.SELLING_WAITING,
                OrderStateDto.SUBMISSION_UNKNOWN -> OrderStateDto.SELLING_IN_PROCESS
                OrderStateDto.SELLING_IN_PROCESS,
                OrderStateDto.SELLING_COMPLETED -> this
                else -> null
            }
            OrderStateDto.SUBMIT_FAILED -> when (this) {
                OrderStateDto.SELLING_WAITING,
                OrderStateDto.SELLING_IN_PROCESS,
                OrderStateDto.SUBMISSION_UNKNOWN -> OrderStateDto.SUBMIT_FAILED
                OrderStateDto.SUBMIT_FAILED,
                OrderStateDto.SELLING_COMPLETED -> this
                else -> null
            }
            OrderStateDto.SELLING_COMPLETED -> when (this) {
                OrderStateDto.SELLING_WAITING,
                OrderStateDto.SELLING_IN_PROCESS,
                OrderStateDto.SUBMISSION_UNKNOWN -> OrderStateDto.SELLING_COMPLETED
                OrderStateDto.SELLING_COMPLETED -> this
                else -> null
            }
            else -> null
        }
    }

    private fun String.toStockOrderMarket(): StockOrderMarket {
        return when {
            length == 6 && all(Char::isDigit) -> StockOrderMarket.DOMESTIC
            else -> StockOrderMarket.OVERSEAS_US
        }
    }

    private fun OrderIntentSide.toExecutionType(): ExecutionTypeDto {
        return when (this) {
            OrderIntentSide.SELL -> ExecutionTypeDto.SELLING
            OrderIntentSide.BUY -> ExecutionTypeDto.PURCHASE
        }
    }

    private enum class RecoveryMode {
        UNKNOWN_SUBMISSION,
        CANCEL_PENDING,
    }
}

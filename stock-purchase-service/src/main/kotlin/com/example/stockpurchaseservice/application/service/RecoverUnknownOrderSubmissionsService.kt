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
import java.time.ZonedDateTime
import java.util.UUID

@UseCaseImpl
class RecoverUnknownOrderSubmissionsService(
    private val marketService: MarketServicePort,
    private val orderIntentSubmissionPort: OrderIntentSubmissionPort,
    private val orderExecutionEventPort: OrderExecutionEventPort,
    private val operationalAlertPort: OperationalAlertPort,
) : RecoverUnknownOrderSubmissionsUseCase {

    override suspend fun execute() {
        orderIntentSubmissionPort.findUnknownSubmissions().forEach { submission ->
            recover(submission)
        }
    }

    private suspend fun recover(submission: OrderIntentSubmissionDto) {
        val status = marketService.findOrderSubmissionStatus(submission.toQuery())
        when (status.status) {
            BrokerOrderStatus.SUBMITTED -> markSubmitted(submission, status)
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
            side = side,
            market = symbol.toStockOrderMarket(),
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
}

package com.example.stockpurchaseservice.adapter.out.observability

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.out.OrderCancellationSubmissionAlert
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionFailureAlert
import com.example.stockpurchaseservice.application.port.out.ReconciliationFailureAlert
import com.example.stockpurchaseservice.application.port.out.SubmissionUnknownAlert
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionAlert
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory

@ExternalApiAdapter
internal class LoggingOperationalAlertAdapter(
    private val meterRegistry: MeterRegistry,
    private val notificationSink: OperationalAlertNotificationSink,
) : OperationalAlertPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun alertOrderSubmissionFailed(alert: OrderSubmissionFailureAlert) {
        emitAlert(
            OperationalAlertNotification(
                type = "order_submission_failed",
                severity = "error",
                title = "Order submission failed",
                occurredAt = alert.occurredAt,
                attributes = mapOf(
                    "orderIntentId" to alert.orderIntentId.toString(),
                    "strategyExecutionId" to alert.strategyExecutionId,
                    "symbol" to alert.symbol,
                    "side" to alert.side.name,
                    "orderType" to alert.orderType.name,
                    "orderTag" to alert.orderTag,
                    "reason" to alert.reason,
                ),
            ),
        )
        log.error(
            "Order submission failed: orderIntentId={} strategyExecutionId={} symbol={} side={} orderType={} orderTag={} reason={}",
            alert.orderIntentId,
            alert.strategyExecutionId,
            alert.symbol,
            alert.side,
            alert.orderType,
            alert.orderTag,
            alert.reason,
        )
    }

    override suspend fun alertSubmissionUnknown(alert: SubmissionUnknownAlert) {
        val severity = if (alert.persistent) "error" else "warning"
        emitAlert(
            OperationalAlertNotification(
                type = "submission_unknown",
                severity = severity,
                title = if (alert.persistent) {
                    "Order submission persistently unknown"
                } else {
                    "Order submission remains unknown"
                },
                occurredAt = alert.checkedAt,
                attributes = mapOf(
                    "orderIntentId" to alert.orderIntentId.toString(),
                    "strategyExecutionId" to alert.strategyExecutionId,
                    "symbol" to alert.symbol,
                    "side" to alert.side.name,
                    "orderType" to alert.orderType.name,
                    "orderTag" to alert.orderTag,
                    "externalOrderId" to alert.externalOrderId,
                    "submittedAt" to alert.submittedAt.toString(),
                    "ageSeconds" to alert.ageSeconds?.toString(),
                    "persistent" to alert.persistent.toString(),
                    "reason" to alert.reason,
                ),
            ),
        )
        val message =
            "Order submission remains unknown: orderIntentId={} strategyExecutionId={} symbol={} side={} " +
                "orderType={} orderTag={} externalOrderId={} submittedAt={} checkedAt={} ageSeconds={} " +
                "persistent={} reason={}"
        if (alert.persistent) {
            log.error(
                message,
                alert.orderIntentId,
                alert.strategyExecutionId,
                alert.symbol,
                alert.side,
                alert.orderType,
                alert.orderTag,
                alert.externalOrderId,
                alert.submittedAt,
                alert.checkedAt,
                alert.ageSeconds,
                alert.persistent,
                alert.reason,
            )
        } else {
            log.warn(
                message,
                alert.orderIntentId,
                alert.strategyExecutionId,
                alert.symbol,
                alert.side,
                alert.orderType,
                alert.orderTag,
                alert.externalOrderId,
                alert.submittedAt,
                alert.checkedAt,
                alert.ageSeconds,
                alert.persistent,
                alert.reason,
            )
        }
    }

    override suspend fun alertReconciliationFailed(alert: ReconciliationFailureAlert) {
        emitAlert(
            OperationalAlertNotification(
                type = "reconciliation_failed",
                severity = "error",
                title = "Execution reconciliation failed",
                occurredAt = alert.failedAt,
                attributes = mapOf(
                    "source" to alert.source,
                    "reason" to alert.reason,
                ),
            ),
        )
        log.error(
            "Execution reconciliation failed: source={} failedAt={} reason={}",
            alert.source,
            alert.failedAt,
            alert.reason,
        )
    }

    override suspend fun alertUnmatchedExecution(alert: UnmatchedExecutionAlert) {
        emitAlert(
            OperationalAlertNotification(
                type = "unmatched_execution",
                severity = "warning",
                title = "Unmatched broker execution observed",
                occurredAt = alert.observedAt,
                attributes = mapOf(
                    "source" to alert.source,
                    "externalExecutionId" to alert.externalExecutionId,
                    "externalOrderId" to alert.externalOrderId,
                    "stockId" to alert.stockId,
                    "quantity" to alert.quantity.toString(),
                    "type" to alert.type.name,
                    "reason" to alert.reason,
                ),
            ),
        )
        log.warn(
            "Unmatched broker execution observed: source={} externalExecutionId={} externalOrderId={} stockId={} quantity={} type={} observedAt={} reason={}",
            alert.source,
            alert.externalExecutionId,
            alert.externalOrderId,
            alert.stockId,
            alert.quantity,
            alert.type,
            alert.observedAt,
            alert.reason,
        )
    }

    override suspend fun alertOrderCancellationSubmissionFailed(alert: OrderCancellationSubmissionAlert) {
        emitAlert(
            OperationalAlertNotification(
                type = "order_cancellation_submission_failed",
                severity = "error",
                title = "Order cancellation submission failed",
                occurredAt = alert.occurredAt,
                attributes = mapOf(
                    "cancellationRequestId" to alert.cancellationRequestId.toString(),
                    "strategyExecutionId" to alert.strategyExecutionId,
                    "symbol" to alert.symbol,
                    "originalBrokerOrderId" to alert.originalBrokerOrderId,
                    "branchOrderNumber" to alert.branchOrderNumber,
                    "reason" to alert.reason,
                ),
            ),
        )
        log.error(
            "Order cancellation submission failed: cancellationRequestId={} strategyExecutionId={} symbol={} originalBrokerOrderId={} branchOrderNumber={} reason={}",
            alert.cancellationRequestId,
            alert.strategyExecutionId,
            alert.symbol,
            alert.originalBrokerOrderId,
            alert.branchOrderNumber,
            alert.reason,
        )
    }

    override suspend fun alertOrderCancellationSubmissionUnknown(alert: OrderCancellationSubmissionAlert) {
        emitAlert(
            OperationalAlertNotification(
                type = "order_cancellation_submission_unknown",
                severity = "warning",
                title = "Order cancellation submission remains unknown",
                occurredAt = alert.occurredAt,
                attributes = mapOf(
                    "cancellationRequestId" to alert.cancellationRequestId.toString(),
                    "strategyExecutionId" to alert.strategyExecutionId,
                    "symbol" to alert.symbol,
                    "originalBrokerOrderId" to alert.originalBrokerOrderId,
                    "branchOrderNumber" to alert.branchOrderNumber,
                    "reason" to alert.reason,
                ),
            ),
        )
        log.warn(
            "Order cancellation submission remains unknown: cancellationRequestId={} strategyExecutionId={} symbol={} originalBrokerOrderId={} branchOrderNumber={} reason={}",
            alert.cancellationRequestId,
            alert.strategyExecutionId,
            alert.symbol,
            alert.originalBrokerOrderId,
            alert.branchOrderNumber,
            alert.reason,
        )
    }

    private suspend fun emitAlert(notification: OperationalAlertNotification) {
        recordAlert(type = notification.type, severity = notification.severity)
        runCatching {
            notificationSink.send(notification)
        }.onFailure { exception ->
            log.warn(
                "Failed to send operational alert notification: type={} severity={}",
                notification.type,
                notification.severity,
                exception,
            )
        }
    }

    private fun recordAlert(type: String, severity: String) {
        runCatching {
            Counter.builder(OPERATIONAL_ALERT_COUNTER)
                .description("Operational alerts raised by stock-purchase-service")
                .tag("type", type)
                .tag("severity", severity)
                .register(meterRegistry)
                .increment()
        }.onFailure { exception ->
            log.warn("Failed to record operational alert metric: type={} severity={}", type, severity, exception)
        }
    }

    companion object {
        internal const val OPERATIONAL_ALERT_COUNTER = "stock.purchase.operational.alerts"
    }
}

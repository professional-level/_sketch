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
) : OperationalAlertPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun alertOrderSubmissionFailed(alert: OrderSubmissionFailureAlert) {
        recordAlert(type = "order_submission_failed", severity = "error")
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
        recordAlert(type = "submission_unknown", severity = "warning")
        log.warn(
            "Order submission remains unknown: orderIntentId={} strategyExecutionId={} symbol={} side={} orderType={} orderTag={} externalOrderId={} submittedAt={} checkedAt={} reason={}",
            alert.orderIntentId,
            alert.strategyExecutionId,
            alert.symbol,
            alert.side,
            alert.orderType,
            alert.orderTag,
            alert.externalOrderId,
            alert.submittedAt,
            alert.checkedAt,
            alert.reason,
        )
    }

    override suspend fun alertReconciliationFailed(alert: ReconciliationFailureAlert) {
        recordAlert(type = "reconciliation_failed", severity = "error")
        log.error(
            "Execution reconciliation failed: source={} failedAt={} reason={}",
            alert.source,
            alert.failedAt,
            alert.reason,
        )
    }

    override suspend fun alertUnmatchedExecution(alert: UnmatchedExecutionAlert) {
        recordAlert(type = "unmatched_execution", severity = "warning")
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
        recordAlert(type = "order_cancellation_submission_failed", severity = "error")
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
        recordAlert(type = "order_cancellation_submission_unknown", severity = "warning")
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

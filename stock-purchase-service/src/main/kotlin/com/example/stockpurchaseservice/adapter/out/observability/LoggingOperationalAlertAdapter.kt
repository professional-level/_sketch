package com.example.stockpurchaseservice.adapter.out.observability

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.out.OrderCancellationSubmissionAlert
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionFailureAlert
import com.example.stockpurchaseservice.application.port.out.ReconciliationFailureAlert
import com.example.stockpurchaseservice.application.port.out.SubmissionUnknownAlert
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionAlert
import org.slf4j.LoggerFactory

@ExternalApiAdapter
internal class LoggingOperationalAlertAdapter : OperationalAlertPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun alertOrderSubmissionFailed(alert: OrderSubmissionFailureAlert) {
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
        log.error(
            "Execution reconciliation failed: source={} failedAt={} reason={}",
            alert.source,
            alert.failedAt,
            alert.reason,
        )
    }

    override suspend fun alertUnmatchedExecution(alert: UnmatchedExecutionAlert) {
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
}

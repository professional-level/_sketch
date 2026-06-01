package com.example.stockpurchaseservice.adapter.out.observability

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.OrderCancellationSubmissionAlert
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionFailureAlert
import com.example.stockpurchaseservice.application.port.out.ReconciliationFailureAlert
import com.example.stockpurchaseservice.application.port.out.SubmissionUnknownAlert
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionAlert
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class LoggingOperationalAlertAdapterTest {

    @Test
    fun `records submission alert counters with severity tags`() = runBlocking {
        val meterRegistry = SimpleMeterRegistry()
        val sink = FakeOperationalAlertNotificationSink()
        val adapter = LoggingOperationalAlertAdapter(meterRegistry, sink)

        adapter.alertOrderSubmissionFailed(orderSubmissionFailureAlert())
        adapter.alertSubmissionUnknown(submissionUnknownAlert())

        assertEquals(1.0, meterRegistry.alertCount("order_submission_failed", "error"))
        assertEquals(1.0, meterRegistry.alertCount("submission_unknown", "warning"))
        assertEquals(listOf("order_submission_failed", "submission_unknown"), sink.sent.map { it.type })
    }

    @Test
    fun `records reconciliation and cancellation alert counters`() = runBlocking {
        val meterRegistry = SimpleMeterRegistry()
        val sink = FakeOperationalAlertNotificationSink()
        val adapter = LoggingOperationalAlertAdapter(meterRegistry, sink)

        adapter.alertReconciliationFailed(reconciliationFailureAlert())
        adapter.alertUnmatchedExecution(unmatchedExecutionAlert())
        adapter.alertOrderCancellationSubmissionFailed(orderCancellationAlert())
        adapter.alertOrderCancellationSubmissionUnknown(orderCancellationAlert())

        assertEquals(1.0, meterRegistry.alertCount("reconciliation_failed", "error"))
        assertEquals(1.0, meterRegistry.alertCount("unmatched_execution", "warning"))
        assertEquals(1.0, meterRegistry.alertCount("order_cancellation_submission_failed", "error"))
        assertEquals(1.0, meterRegistry.alertCount("order_cancellation_submission_unknown", "warning"))
        assertEquals(
            listOf(
                "reconciliation_failed",
                "unmatched_execution",
                "order_cancellation_submission_failed",
                "order_cancellation_submission_unknown",
            ),
            sink.sent.map { it.type },
        )
    }

    @Test
    fun `continues recording metric when notification sink fails`() = runBlocking {
        val meterRegistry = SimpleMeterRegistry()
        val adapter = LoggingOperationalAlertAdapter(
            meterRegistry = meterRegistry,
            notificationSink = FakeOperationalAlertNotificationSink(failure = IllegalStateException("webhook down")),
        )

        adapter.alertOrderSubmissionFailed(orderSubmissionFailureAlert())

        assertEquals(1.0, meterRegistry.alertCount("order_submission_failed", "error"))
    }

    private fun SimpleMeterRegistry.alertCount(type: String, severity: String): Double {
        return find(LoggingOperationalAlertAdapter.OPERATIONAL_ALERT_COUNTER)
            .tag("type", type)
            .tag("severity", severity)
            .counter()
            ?.count()
            ?: 0.0
    }

    private fun orderSubmissionFailureAlert(): OrderSubmissionFailureAlert {
        return OrderSubmissionFailureAlert(
            orderIntentId = UUID.randomUUID(),
            idempotencyKey = "submit-failure",
            strategyExecutionId = "laor-v4:TQQQ",
            symbol = "TQQQ",
            side = OrderIntentSide.BUY,
            orderType = OrderIntentType.LOC,
            orderTag = "FIRST_BUY",
            reason = "broker timeout",
            occurredAt = ALERT_TIME,
        )
    }

    private fun submissionUnknownAlert(): SubmissionUnknownAlert {
        return SubmissionUnknownAlert(
            orderIntentId = UUID.randomUUID(),
            idempotencyKey = "submit-unknown",
            strategyExecutionId = "laor-v4:TQQQ",
            symbol = "TQQQ",
            side = OrderIntentSide.BUY,
            orderType = OrderIntentType.LOC,
            orderTag = "FIRST_BUY",
            externalOrderId = null,
            reason = "timeout after broker submit",
            submittedAt = ALERT_TIME.minusMinutes(5),
            checkedAt = ALERT_TIME,
        )
    }

    private fun reconciliationFailureAlert(): ReconciliationFailureAlert {
        return ReconciliationFailureAlert(
            source = "BROKER_EXECUTION_DAILY",
            reason = "broker unavailable",
            failedAt = ALERT_TIME,
        )
    }

    private fun unmatchedExecutionAlert(): UnmatchedExecutionAlert {
        return UnmatchedExecutionAlert(
            source = "BROKER_EXECUTION_DAILY",
            externalExecutionId = "exec-1",
            externalOrderId = "order-1",
            stockId = "TQQQ",
            quantity = 1,
            type = ExecutionTypeDto.PURCHASE,
            reason = "broker order not found",
            observedAt = ALERT_TIME,
        )
    }

    private fun orderCancellationAlert(): OrderCancellationSubmissionAlert {
        return OrderCancellationSubmissionAlert(
            cancellationRequestId = UUID.randomUUID(),
            idempotencyKey = "cancel-1",
            strategyExecutionId = "laor-v4:TQQQ",
            symbol = "TQQQ",
            originalBrokerOrderId = "order-1",
            branchOrderNumber = null,
            reason = "cancel timeout",
            occurredAt = ALERT_TIME,
        )
    }

    private class FakeOperationalAlertNotificationSink(
        private val failure: RuntimeException? = null,
    ) : OperationalAlertNotificationSink {
        val sent: MutableList<OperationalAlertNotification> = mutableListOf()

        override suspend fun send(notification: OperationalAlertNotification) {
            failure?.let { throw it }
            sent += notification
        }
    }

    private companion object {
        val ALERT_TIME: ZonedDateTime = ZonedDateTime.parse("2026-06-01T09:00:00+09:00")
    }
}

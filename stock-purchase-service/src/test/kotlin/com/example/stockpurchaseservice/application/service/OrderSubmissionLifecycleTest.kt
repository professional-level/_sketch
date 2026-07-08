package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderSubmissionLifecycleTest {

    @Test
    fun `unknown submission transitions are deterministic`() {
        assertEquals(
            OrderSubmissionTransition.MARK_SUBMITTED_AND_PUBLISH,
            OrderSubmissionLifecycle.recoverUnknown(BrokerOrderStatus.SUBMITTED),
        )
        assertEquals(
            OrderSubmissionTransition.MARK_SUBMITTED_AND_RECOVER_FILL,
            OrderSubmissionLifecycle.recoverUnknown(BrokerOrderStatus.PARTIALLY_FILLED),
        )
        assertEquals(
            OrderSubmissionTransition.MARK_SUBMITTED_AND_RECOVER_FILL,
            OrderSubmissionLifecycle.recoverUnknown(BrokerOrderStatus.FILLED),
        )
        assertEquals(
            OrderSubmissionTransition.MARK_REJECTED,
            OrderSubmissionLifecycle.recoverUnknown(BrokerOrderStatus.REJECTED),
        )
        assertEquals(
            OrderSubmissionTransition.RECOVER_FILL_AND_MARK_CANCELLED,
            OrderSubmissionLifecycle.recoverUnknown(BrokerOrderStatus.CANCELLED),
        )
        assertEquals(
            OrderSubmissionTransition.KEEP_UNKNOWN,
            OrderSubmissionLifecycle.recoverUnknown(BrokerOrderStatus.UNKNOWN),
        )
    }

    @Test
    fun `cancel pending transitions wait for broker confirmed cancellation`() {
        assertEquals(
            OrderSubmissionTransition.RECOVER_FILL_AND_MARK_CANCELLED,
            OrderSubmissionLifecycle.recoverCancelPending(BrokerOrderStatus.CANCELLED),
        )
        assertEquals(
            OrderSubmissionTransition.MARK_SUBMITTED_WITHOUT_REPUBLISH_AND_RECOVER_FILL,
            OrderSubmissionLifecycle.recoverCancelPending(BrokerOrderStatus.FILLED),
        )
        assertEquals(
            OrderSubmissionTransition.MARK_REJECTED,
            OrderSubmissionLifecycle.recoverCancelPending(BrokerOrderStatus.REJECTED),
        )
        assertEquals(
            OrderSubmissionTransition.KEEP_CANCEL_PENDING_AND_RECOVER_FILL,
            OrderSubmissionLifecycle.recoverCancelPending(BrokerOrderStatus.PARTIALLY_FILLED),
        )
        assertEquals(
            OrderSubmissionTransition.KEEP_CANCEL_PENDING,
            OrderSubmissionLifecycle.recoverCancelPending(BrokerOrderStatus.SUBMITTED),
        )
        assertEquals(
            OrderSubmissionTransition.KEEP_CANCEL_PENDING,
            OrderSubmissionLifecycle.recoverCancelPending(BrokerOrderStatus.UNKNOWN),
        )
    }
}

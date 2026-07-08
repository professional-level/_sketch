package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus

internal object OrderSubmissionLifecycle {

    fun recoverUnknown(observed: BrokerOrderStatus): OrderSubmissionTransition {
        return when (observed) {
            BrokerOrderStatus.SUBMITTED -> OrderSubmissionTransition.MARK_SUBMITTED_AND_PUBLISH
            BrokerOrderStatus.PARTIALLY_FILLED -> OrderSubmissionTransition.MARK_SUBMITTED_AND_RECOVER_FILL
            BrokerOrderStatus.FILLED -> OrderSubmissionTransition.MARK_SUBMITTED_AND_RECOVER_FILL
            BrokerOrderStatus.REJECTED -> OrderSubmissionTransition.MARK_REJECTED
            BrokerOrderStatus.CANCELLED -> OrderSubmissionTransition.RECOVER_FILL_AND_MARK_CANCELLED
            BrokerOrderStatus.UNKNOWN -> OrderSubmissionTransition.KEEP_UNKNOWN
        }
    }

    fun recoverCancelPending(observed: BrokerOrderStatus): OrderSubmissionTransition {
        return when (observed) {
            BrokerOrderStatus.CANCELLED -> OrderSubmissionTransition.RECOVER_FILL_AND_MARK_CANCELLED
            BrokerOrderStatus.REJECTED -> OrderSubmissionTransition.MARK_REJECTED
            BrokerOrderStatus.FILLED -> OrderSubmissionTransition.MARK_SUBMITTED_WITHOUT_REPUBLISH_AND_RECOVER_FILL
            BrokerOrderStatus.PARTIALLY_FILLED -> OrderSubmissionTransition.KEEP_CANCEL_PENDING_AND_RECOVER_FILL
            BrokerOrderStatus.SUBMITTED -> OrderSubmissionTransition.KEEP_CANCEL_PENDING
            BrokerOrderStatus.UNKNOWN -> OrderSubmissionTransition.KEEP_CANCEL_PENDING
        }
    }
}

internal enum class OrderSubmissionTransition {
    MARK_SUBMITTED_AND_PUBLISH,
    MARK_SUBMITTED_AND_RECOVER_FILL,
    MARK_SUBMITTED_WITHOUT_REPUBLISH_AND_RECOVER_FILL,
    MARK_REJECTED,
    RECOVER_FILL_AND_MARK_CANCELLED,
    KEEP_UNKNOWN,
    KEEP_CANCEL_PENDING,
    KEEP_CANCEL_PENDING_AND_RECOVER_FILL,
}

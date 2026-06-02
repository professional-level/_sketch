package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OrderCancelledMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionEventPort
import com.example.stockpurchaseservice.application.port.out.OrderFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderPartiallyFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionFailureAlert
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.ReconciliationFailureAlert
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.SubmissionUnknownAlert
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RecoverUnknownOrderSubmissionsServiceTest {

    @Test
    fun `recovers unknown submission to submitted event`() = runBlocking {
        val submissionPort = FakeOrderIntentSubmissionPort(listOf(submission()))
        val eventPort = FakeOrderExecutionEventPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.SUBMITTED,
                    externalOrderId = "broker-1",
                    checkedAt = CHECKED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
        )

        service.execute()

        assertEquals("broker-1", submissionPort.submitted.single().externalOrderId)
        assertEquals("broker-1", eventPort.submitted.single().brokerOrderId)
        assertEquals(emptyList(), eventPort.rejected)
        assertEquals(emptyList(), eventPort.cancelled)
    }

    @Test
    fun `recovers unknown submission to cancelled event`() = runBlocking {
        val submissionPort = FakeOrderIntentSubmissionPort(listOf(submission(externalOrderId = "broker-1")))
        val eventPort = FakeOrderExecutionEventPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.CANCELLED,
                    externalOrderId = "broker-1",
                    reason = "LOC expired",
                    checkedAt = CHECKED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
        )

        service.execute()

        assertEquals("broker-1", submissionPort.cancelled.single().externalOrderId)
        assertEquals("broker-1", eventPort.cancelled.single().brokerOrderId)
        assertEquals("LOC expired", eventPort.cancelled.single().reason)
    }

    @Test
    fun `keeps unknown submission when broker status is still unknown`() = runBlocking {
        val original = submission(externalOrderId = "broker-1")
        val submissionPort = FakeOrderIntentSubmissionPort(listOf(original))
        val eventPort = FakeOrderExecutionEventPort()
        val alertPort = FakeOperationalAlertPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.UNKNOWN,
                    externalOrderId = "broker-1",
                    reason = "not found yet",
                    checkedAt = CHECKED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = alertPort,
        )

        service.execute()

        assertEquals("not found yet", submissionPort.unknown.single().statusReason)
        assertEquals(CHECKED_AT, submissionPort.unknown.single().lastStatusCheckedAt)
        assertEquals(emptyList(), eventPort.submitted)
        assertEquals(emptyList(), eventPort.rejected)
        assertEquals(emptyList(), eventPort.cancelled)
        assertEquals(ORDER_INTENT_ID, alertPort.submissionUnknown.single().orderIntentId)
        assertEquals("not found yet", alertPort.submissionUnknown.single().reason)
    }

    @Test
    fun `keeps cancel pending submission without republishing submitted event`() = runBlocking {
        val pending = submission(externalOrderId = "broker-1").copy(
            status = OrderIntentSubmissionStatusDto.CANCEL_PENDING,
            statusReason = "cancel request accepted by broker",
        )
        val submissionPort = FakeOrderIntentSubmissionPort(
            unknownSubmissions = emptyList(),
            cancelPendingSubmissions = listOf(pending),
        )
        val eventPort = FakeOrderExecutionEventPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.SUBMITTED,
                    externalOrderId = "broker-1",
                    checkedAt = CHECKED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
        )

        service.execute()

        assertEquals(OrderIntentSubmissionStatusDto.CANCEL_PENDING, submissionPort.cancelPending.single().status)
        assertEquals(CHECKED_AT, submissionPort.cancelPending.single().lastStatusCheckedAt)
        assertEquals(emptyList(), eventPort.submitted)
        assertEquals(emptyList(), eventPort.cancelled)
    }

    @Test
    fun `recovers cancel pending submission to cancelled event`() = runBlocking {
        val pending = submission(externalOrderId = "broker-1").copy(
            status = OrderIntentSubmissionStatusDto.CANCEL_PENDING,
            statusReason = "cancel request accepted by broker",
        )
        val submissionPort = FakeOrderIntentSubmissionPort(
            unknownSubmissions = emptyList(),
            cancelPendingSubmissions = listOf(pending),
        )
        val eventPort = FakeOrderExecutionEventPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.CANCELLED,
                    externalOrderId = "broker-1",
                    reason = "cancel confirmed",
                    checkedAt = CHECKED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
        )

        service.execute()

        assertEquals("broker-1", submissionPort.cancelled.single().externalOrderId)
        assertEquals("broker-1", eventPort.cancelled.single().brokerOrderId)
        assertEquals("cancel confirmed", eventPort.cancelled.single().reason)
        assertEquals(emptyList(), eventPort.submitted)
    }

    private fun submission(externalOrderId: String? = null): OrderIntentSubmissionDto {
        return OrderIntentSubmissionDto(
            orderIntentId = ORDER_INTENT_ID,
            idempotencyKey = "unknown-buy",
            strategyExecutionId = "laor-v4-strategy:TQQQ",
            symbol = "TQQQ",
            side = OrderIntentSide.BUY,
            orderType = OrderIntentType.LOC,
            submittedPrice = 112.0,
            quantity = 3,
            orderTag = "FIRST_BUY",
            internalOrderId = INTERNAL_ORDER_ID,
            externalOrderId = externalOrderId,
            submittedAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
        )
    }

    private class FakeMarketServicePort(
        private val status: BrokerOrderStatusDto,
    ) : MarketServicePort {
        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto(externalOrderId = "broker-buy")
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto(externalOrderId = "broker-sell")
        }

        override fun findExecutionListAtOneDay(): List<ExecutedStockDto> = emptyList()

        override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
            assertEquals(ORDER_INTENT_ID, query.orderIntentId)
            assertEquals(INTERNAL_ORDER_ID, query.internalOrderId)
            return status
        }
    }

    private class FakeOrderIntentSubmissionPort(
        private val unknownSubmissions: List<OrderIntentSubmissionDto>,
        private val cancelPendingSubmissions: List<OrderIntentSubmissionDto> = emptyList(),
    ) : OrderIntentSubmissionPort {
        val submitted: MutableList<OrderIntentSubmissionDto> = mutableListOf()
        val unknown: MutableList<OrderIntentSubmissionDto> = mutableListOf()
        val rejected: MutableList<OrderIntentSubmissionDto> = mutableListOf()
        val cancelled: MutableList<OrderIntentSubmissionDto> = mutableListOf()
        val cancelPending: MutableList<OrderIntentSubmissionDto> = mutableListOf()

        override suspend fun saveSubmitted(submission: OrderIntentSubmissionDto) {
            submitted += submission
        }

        override suspend fun saveUnknown(submission: OrderIntentSubmissionDto) {
            unknown += submission
        }

        override suspend fun saveRejected(submission: OrderIntentSubmissionDto) {
            rejected += submission
        }

        override suspend fun saveCancelled(submission: OrderIntentSubmissionDto) {
            cancelled += submission
        }

        override suspend fun saveCancelPending(submission: OrderIntentSubmissionDto) {
            cancelPending += submission.copy(status = OrderIntentSubmissionStatusDto.CANCEL_PENDING)
        }

        override suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionDto? = null

        override suspend fun findUnknownSubmissions(): List<OrderIntentSubmissionDto> {
            return unknownSubmissions
        }

        override suspend fun findCancelPendingSubmissions(): List<OrderIntentSubmissionDto> {
            return cancelPendingSubmissions
        }
    }

    private class FakeOrderExecutionEventPort : OrderExecutionEventPort {
        val submitted: MutableList<OrderSubmittedMessage> = mutableListOf()
        val rejected: MutableList<OrderRejectedMessage> = mutableListOf()
        val cancelled: MutableList<OrderCancelledMessage> = mutableListOf()

        override suspend fun publishSubmitted(event: OrderSubmittedMessage) {
            submitted += event
        }

        override suspend fun publishRejected(event: OrderRejectedMessage) {
            rejected += event
        }

        override suspend fun publishCancelled(event: OrderCancelledMessage) {
            cancelled += event
        }

        override suspend fun publishFilled(event: OrderFilledMessage) = Unit

        override suspend fun publishPartiallyFilled(event: OrderPartiallyFilledMessage) = Unit
    }

    private class FakeOperationalAlertPort : OperationalAlertPort {
        val orderSubmissionFailed: MutableList<OrderSubmissionFailureAlert> = mutableListOf()
        val submissionUnknown: MutableList<SubmissionUnknownAlert> = mutableListOf()
        val reconciliationFailed: MutableList<ReconciliationFailureAlert> = mutableListOf()

        override suspend fun alertOrderSubmissionFailed(alert: OrderSubmissionFailureAlert) {
            orderSubmissionFailed += alert
        }

        override suspend fun alertSubmissionUnknown(alert: SubmissionUnknownAlert) {
            submissionUnknown += alert
        }

        override suspend fun alertReconciliationFailed(alert: ReconciliationFailureAlert) {
            reconciliationFailed += alert
        }
    }

    companion object {
        private val ORDER_INTENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private val INTERNAL_ORDER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        private val CHECKED_AT: ZonedDateTime = ZonedDateTime.parse("2026-06-02T09:00:00+09:00")
    }
}

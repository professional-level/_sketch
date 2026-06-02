package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionCommand
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.BrokerOrderRejectedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionUnknownException
import com.example.stockpurchaseservice.application.port.out.CancelOrderDto
import com.example.stockpurchaseservice.application.port.out.ExecutionLookupQuery
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.OrderCancellationSubmissionAlert
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionFailureAlert
import com.example.stockpurchaseservice.application.port.out.ProcessedEventPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.ReconciliationFailureAlert
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import com.example.stockpurchaseservice.application.port.out.SubmissionUnknownAlert
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CancelOrderSubmissionServiceTest {

    @Test
    fun `submits cancel request to market port`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort()
        val submissionPort = FakeOrderIntentSubmissionPort(
            originalSubmission(externalOrderId = "broker-order-1", exchange = "NYSE"),
        )
        val service = CancelOrderSubmissionService(
            marketPort,
            processedEventPort,
            FakeOperationalAlertPort(),
            submissionPort,
        )
        val eventId = UUID.randomUUID()

        val result = service.execute(
            cancelCommand(
                eventId = eventId,
                symbol = "TQQQ",
                originalBrokerOrderId = "broker-order-1",
                quantity = 3,
                orderType = OrderIntentType.LOC,
                price = 112.5,
                cancelAll = false,
                exchange = "AMEX",
            ),
        )

        assertEquals(CancelOrderSubmissionStatus.ACCEPTED, result.status)
        assertEquals("cancel-broker-1", result.brokerOrderId)
        assertEquals(eventId, processedEventPort.succeeded.single())
        assertEquals(OrderIntentSubmissionStatusDto.CANCEL_PENDING, submissionPort.cancelPending.single().status)
        assertEquals("cancel request accepted by broker", submissionPort.cancelPending.single().statusReason)
        assertEquals(ZonedDateTime.parse("2026-06-02T09:00:00+09:00"), submissionPort.cancelPending.single().lastStatusCheckedAt)
        with(marketPort.cancelOrders.single()) {
            assertEquals("TQQQ", stockId)
            assertEquals("broker-order-1", originalOrderId)
            assertEquals(3, quantity)
            assertEquals(StockOrderMarket.OVERSEAS_US, market)
            assertEquals(StockOrderType.LOC, orderType)
            assertEquals(112.5, price)
            assertEquals(false, cancelAll)
            assertEquals("NYSE", exchange)
        }
    }

    @Test
    fun `routes six digit cancel request to domestic market`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val service = CancelOrderSubmissionService(
            marketPort,
            FakeProcessedEventPort(),
            FakeOperationalAlertPort(),
            FakeOrderIntentSubmissionPort(),
        )

        service.execute(
            cancelCommand(
                symbol = "005930",
                originalBrokerOrderId = "domestic-order-1",
                branchOrderNumber = "00001",
                quantity = 1,
            ),
        )

        with(marketPort.cancelOrders.single()) {
            assertEquals(StockOrderMarket.DOMESTIC, market)
            assertEquals("00001", branchOrderNumber)
        }
    }

    @Test
    fun `uses stored branch order number for domestic cancel request`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val service = CancelOrderSubmissionService(
            marketPort,
            FakeProcessedEventPort(),
            FakeOperationalAlertPort(),
            FakeOrderIntentSubmissionPort(
                originalSubmission(
                    symbol = "005930",
                    externalOrderId = "domestic-order-1",
                    branchOrderNumber = "00001",
                ),
            ),
        )

        service.execute(
            cancelCommand(
                symbol = "005930",
                originalBrokerOrderId = "domestic-order-1",
                branchOrderNumber = null,
                quantity = 1,
            ),
        )

        with(marketPort.cancelOrders.single()) {
            assertEquals(StockOrderMarket.DOMESTIC, market)
            assertEquals("domestic-order-1", originalOrderId)
            assertEquals("00001", branchOrderNumber)
        }
    }

    @Test
    fun `skips duplicate cancel request`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort(started = false)
        val service = CancelOrderSubmissionService(
            marketPort,
            processedEventPort,
            FakeOperationalAlertPort(),
            FakeOrderIntentSubmissionPort(),
        )

        val result = service.execute(cancelCommand())

        assertEquals(CancelOrderSubmissionStatus.SKIPPED_DUPLICATE, result.status)
        assertEquals(emptyList(), marketPort.cancelOrders)
        assertEquals(emptyList(), processedEventPort.succeeded)
    }

    @Test
    fun `marks unknown cancel submission successful for broker recovery`() = runBlocking {
        val marketPort = FakeMarketServicePort(
            cancelException = BrokerOrderSubmissionUnknownException(
                message = "cancel response timed out",
                externalOrderId = "maybe-cancel-order",
            ),
        )
        val processedEventPort = FakeProcessedEventPort()
        val alertPort = FakeOperationalAlertPort()
        val submissionPort = FakeOrderIntentSubmissionPort(
            originalSubmission(externalOrderId = "broker-order-1"),
        )
        val service = CancelOrderSubmissionService(marketPort, processedEventPort, alertPort, submissionPort)
        val eventId = UUID.randomUUID()

        val result = service.execute(cancelCommand(eventId = eventId))

        assertEquals(CancelOrderSubmissionStatus.SUBMISSION_UNKNOWN, result.status)
        assertEquals("maybe-cancel-order", result.brokerOrderId)
        assertEquals(eventId, processedEventPort.succeeded.single())
        assertEquals(OrderIntentSubmissionStatusDto.CANCEL_PENDING, submissionPort.cancelPending.single().status)
        assertEquals("cancel response timed out", submissionPort.cancelPending.single().statusReason)
        assertEquals(eventId, alertPort.unknownCancellations.single().cancellationRequestId)
        assertEquals(emptyList(), processedEventPort.failed)
    }

    @Test
    fun `marks rejected cancel submission successful without retry`() = runBlocking {
        val marketPort = FakeMarketServicePort(
            cancelException = BrokerOrderRejectedException("cancel rejected by broker"),
        )
        val processedEventPort = FakeProcessedEventPort()
        val alertPort = FakeOperationalAlertPort()
        val service = CancelOrderSubmissionService(
            marketPort,
            processedEventPort,
            alertPort,
            FakeOrderIntentSubmissionPort(),
        )
        val eventId = UUID.randomUUID()

        val result = service.execute(cancelCommand(eventId = eventId))

        assertEquals(CancelOrderSubmissionStatus.REJECTED, result.status)
        assertEquals(eventId, processedEventPort.succeeded.single())
        assertEquals(eventId, alertPort.failedCancellations.single().cancellationRequestId)
        assertEquals(emptyList(), processedEventPort.failed)
    }

    @Test
    fun `passes domestic cancel request without branch order number to broker gateway`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort()
        val alertPort = FakeOperationalAlertPort()
        val service = CancelOrderSubmissionService(
            marketPort,
            processedEventPort,
            alertPort,
            FakeOrderIntentSubmissionPort(),
        )
        val eventId = UUID.randomUUID()

        val result = service.execute(
            cancelCommand(
                eventId = eventId,
                symbol = "005930",
                originalBrokerOrderId = "domestic-order-1",
                branchOrderNumber = null,
            ),
        )

        assertEquals(CancelOrderSubmissionStatus.ACCEPTED, result.status)
        assertEquals(eventId, processedEventPort.succeeded.single())
        with(marketPort.cancelOrders) {
            assertEquals(1, size)
            assertEquals(StockOrderMarket.DOMESTIC, single().market)
            assertEquals(null, single().branchOrderNumber)
        }
        assertEquals(emptyList(), processedEventPort.failed)
        assertEquals(emptyList(), alertPort.failedCancellations)
    }

    private fun cancelCommand(
        eventId: UUID = UUID.randomUUID(),
        symbol: String = "TQQQ",
        originalBrokerOrderId: String = "broker-order-1",
        branchOrderNumber: String? = null,
        quantity: Long = 1,
        orderType: OrderIntentType = OrderIntentType.LIMIT,
        price: Double = 0.0,
        cancelAll: Boolean = true,
        exchange: String = "NASD",
    ): CancelOrderSubmissionCommand {
        return CancelOrderSubmissionCommand(
            eventId = eventId,
            idempotencyKey = "cancel:$symbol:$originalBrokerOrderId",
            strategyExecutionId = "strategy:$symbol",
            symbol = symbol,
            originalBrokerOrderId = originalBrokerOrderId,
            branchOrderNumber = branchOrderNumber,
            quantity = quantity,
            orderType = orderType,
            price = price,
            cancelAll = cancelAll,
            exchange = exchange,
            requestedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
        )
    }

    private fun originalSubmission(
        externalOrderId: String,
        symbol: String = "TQQQ",
        branchOrderNumber: String? = null,
        exchange: String = "NASD",
    ): OrderIntentSubmissionDto {
        return OrderIntentSubmissionDto(
            orderIntentId = UUID.fromString("00000000-0000-0000-0000-000000000111"),
            idempotencyKey = "order:$symbol:$externalOrderId",
            strategyExecutionId = "strategy:$symbol",
            symbol = symbol,
            exchange = exchange,
            side = OrderIntentSide.BUY,
            orderType = OrderIntentType.LIMIT,
            submittedPrice = 100.0,
            quantity = 1,
            orderTag = "FIRST_BUY",
            internalOrderId = UUID.fromString("00000000-0000-0000-0000-000000000222"),
            externalOrderId = externalOrderId,
            branchOrderNumber = branchOrderNumber,
            submittedAt = ZonedDateTime.parse("2026-06-02T08:50:00+09:00"),
        )
    }

    private class FakeMarketServicePort(
        private val cancelException: RuntimeException? = null,
    ) : MarketServicePort {
        val cancelOrders: MutableList<CancelOrderDto> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto("buy-broker-1")
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto("sell-broker-1")
        }

        override fun cancelOrder(order: CancelOrderDto): BrokerOrderSubmissionDto {
            cancelOrders += order
            cancelException?.let { throw it }
            return BrokerOrderSubmissionDto("cancel-broker-1")
        }

        override fun findExecutionList(query: ExecutionLookupQuery): List<ExecutedStockDto> {
            return emptyList()
        }

        override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
            return emptyList()
        }

        override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
            return BrokerOrderStatusDto(BrokerOrderStatus.UNKNOWN)
        }
    }

    private class FakeProcessedEventPort(
        private val started: Boolean = true,
    ) : ProcessedEventPort {
        val succeeded: MutableList<UUID> = mutableListOf()
        val failed: MutableList<UUID> = mutableListOf()

        override suspend fun tryStart(eventId: UUID, idempotencyKey: String): Boolean {
            return started
        }

        override suspend fun markSuccess(eventId: UUID) {
            succeeded += eventId
        }

        override suspend fun markFailed(eventId: UUID, reason: String?) {
            failed += eventId
        }
    }

    private class FakeOrderIntentSubmissionPort(
        private var originalSubmission: OrderIntentSubmissionDto? = null,
    ) : OrderIntentSubmissionPort {
        val cancelPending: MutableList<OrderIntentSubmissionDto> = mutableListOf()

        override suspend fun saveSubmitted(submission: OrderIntentSubmissionDto) = Unit
        override suspend fun saveUnknown(submission: OrderIntentSubmissionDto) = Unit
        override suspend fun saveRejected(submission: OrderIntentSubmissionDto) = Unit
        override suspend fun saveCancelled(submission: OrderIntentSubmissionDto) = Unit

        override suspend fun saveCancelPending(submission: OrderIntentSubmissionDto) {
            cancelPending += submission.copy(status = OrderIntentSubmissionStatusDto.CANCEL_PENDING)
        }

        override suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionDto? {
            return originalSubmission?.takeIf { it.externalOrderId == externalOrderId }
        }

        override suspend fun findUnknownSubmissions(): List<OrderIntentSubmissionDto> = emptyList()
        override suspend fun findCancelPendingSubmissions(): List<OrderIntentSubmissionDto> = emptyList()
    }

    private class FakeOperationalAlertPort : OperationalAlertPort {
        val failedCancellations: MutableList<OrderCancellationSubmissionAlert> = mutableListOf()
        val unknownCancellations: MutableList<OrderCancellationSubmissionAlert> = mutableListOf()

        override suspend fun alertOrderSubmissionFailed(alert: OrderSubmissionFailureAlert) = Unit
        override suspend fun alertSubmissionUnknown(alert: SubmissionUnknownAlert) = Unit
        override suspend fun alertReconciliationFailed(alert: ReconciliationFailureAlert) = Unit

        override suspend fun alertOrderCancellationSubmissionFailed(alert: OrderCancellationSubmissionAlert) {
            failedCancellations += alert
        }

        override suspend fun alertOrderCancellationSubmissionUnknown(alert: OrderCancellationSubmissionAlert) {
            unknownCancellations += alert
        }
    }
}

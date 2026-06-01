package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionCommand
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionStatus
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
import kotlin.test.assertFailsWith

class CancelOrderSubmissionServiceTest {

    @Test
    fun `submits cancel request to market port`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort()
        val service = CancelOrderSubmissionService(
            marketPort,
            processedEventPort,
            FakeOperationalAlertPort(),
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
            ),
        )

        assertEquals(CancelOrderSubmissionStatus.ACCEPTED, result.status)
        assertEquals("cancel-broker-1", result.brokerOrderId)
        assertEquals(eventId, processedEventPort.succeeded.single())
        with(marketPort.cancelOrders.single()) {
            assertEquals("TQQQ", stockId)
            assertEquals("broker-order-1", originalOrderId)
            assertEquals(3, quantity)
            assertEquals(StockOrderMarket.OVERSEAS_US, market)
            assertEquals(StockOrderType.LOC, orderType)
            assertEquals(112.5, price)
            assertEquals(false, cancelAll)
        }
    }

    @Test
    fun `routes six digit cancel request to domestic market`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val service = CancelOrderSubmissionService(
            marketPort,
            FakeProcessedEventPort(),
            FakeOperationalAlertPort(),
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
    fun `skips duplicate cancel request`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort(started = false)
        val service = CancelOrderSubmissionService(
            marketPort,
            processedEventPort,
            FakeOperationalAlertPort(),
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
        val service = CancelOrderSubmissionService(marketPort, processedEventPort, alertPort)
        val eventId = UUID.randomUUID()

        val result = service.execute(cancelCommand(eventId = eventId))

        assertEquals(CancelOrderSubmissionStatus.SUBMISSION_UNKNOWN, result.status)
        assertEquals("maybe-cancel-order", result.brokerOrderId)
        assertEquals(eventId, processedEventPort.succeeded.single())
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
        val service = CancelOrderSubmissionService(marketPort, processedEventPort, alertPort)
        val eventId = UUID.randomUUID()

        val result = service.execute(cancelCommand(eventId = eventId))

        assertEquals(CancelOrderSubmissionStatus.REJECTED, result.status)
        assertEquals(eventId, processedEventPort.succeeded.single())
        assertEquals(eventId, alertPort.failedCancellations.single().cancellationRequestId)
        assertEquals(emptyList(), processedEventPort.failed)
    }

    @Test
    fun `fails domestic cancel request without branch order number`() = runBlocking {
        val processedEventPort = FakeProcessedEventPort()
        val alertPort = FakeOperationalAlertPort()
        val service = CancelOrderSubmissionService(
            FakeMarketServicePort(),
            processedEventPort,
            alertPort,
        )
        val eventId = UUID.randomUUID()

        assertFailsWith<IllegalArgumentException> {
            service.execute(
                cancelCommand(
                    eventId = eventId,
                    symbol = "005930",
                    originalBrokerOrderId = "domestic-order-1",
                    branchOrderNumber = null,
                ),
            )
        }

        assertEquals(eventId, processedEventPort.failed.single())
        assertEquals(eventId, alertPort.failedCancellations.single().cancellationRequestId)
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
            requestedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
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

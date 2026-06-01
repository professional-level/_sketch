package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OrderExecutionEventPort
import com.example.stockpurchaseservice.application.port.out.OrderFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import com.example.stockpurchaseservice.application.port.out.ProcessedEventPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SubmitOrderIntentServiceTest {

    @Test
    fun `submits buy order intent to market port`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort()
        val submissionPort = FakeOrderIntentSubmissionPort()
        val eventPort = FakeOrderExecutionEventPort()
        val service = SubmitOrderIntentService(marketPort, processedEventPort, submissionPort, eventPort)
        val eventId = UUID.randomUUID()

        val result = service.execute(
            SubmitOrderIntentCommand(
                eventId = eventId,
                idempotencyKey = "laor-v4-strategy:TQQQ:2026-05-30:FIRST_BUY:0",
                strategyExecutionId = "laor-v4-strategy:TQQQ",
                symbol = "TQQQ",
                side = OrderIntentSide.BUY,
                orderType = OrderIntentType.LOC,
                price = 112.0,
                quantity = 3,
                orderTag = "FIRST_BUY",
                createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
            ),
        )

        assertEquals(OrderIntentSubmissionStatus.SUBMITTED, result.status)
        assertEquals(eventId, processedEventPort.succeeded.single())
        assertEquals(eventId, submissionPort.saved.single().orderIntentId)
        assertEquals("broker-buy-1", eventPort.submitted.single().brokerOrderId)
        with(marketPort.buyOrders.single()) {
            assertEquals("TQQQ", stockId)
            assertEquals(112.0, purchasePrice)
            assertEquals(3, quantity)
            assertEquals(StockOrderMarket.OVERSEAS_US, market)
            assertEquals(StockOrderType.LOC, orderType)
        }
    }

    @Test
    fun `routes six digit symbols to domestic market`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort()
        val service = SubmitOrderIntentService(
            marketPort,
            processedEventPort,
            FakeOrderIntentSubmissionPort(),
            FakeOrderExecutionEventPort(),
        )

        service.execute(
            SubmitOrderIntentCommand(
                eventId = UUID.randomUUID(),
                idempotencyKey = "domestic-buy",
                strategyExecutionId = "strategy:005930",
                symbol = "005930",
                side = OrderIntentSide.BUY,
                orderType = OrderIntentType.LIMIT,
                price = 70000.0,
                quantity = 2,
                orderTag = "BUY",
                createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
            ),
        )

        with(marketPort.buyOrders.single()) {
            assertEquals(StockOrderMarket.DOMESTIC, market)
            assertEquals(StockOrderType.LIMIT, orderType)
        }
    }

    @Test
    fun `skips duplicate order intent`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort(startResult = false)
        val eventPort = FakeOrderExecutionEventPort()
        val service = SubmitOrderIntentService(
            marketPort,
            processedEventPort,
            FakeOrderIntentSubmissionPort(),
            eventPort,
        )

        val result = service.execute(
            SubmitOrderIntentCommand(
                eventId = UUID.randomUUID(),
                idempotencyKey = "duplicate",
                strategyExecutionId = "laor-v4-strategy:TQQQ",
                symbol = "TQQQ",
                side = OrderIntentSide.SELL,
                orderType = OrderIntentType.MOC,
                price = null,
                quantity = 1,
                orderTag = "REVERSE_MOC_SELL",
                createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
            ),
        )

        assertEquals(OrderIntentSubmissionStatus.SKIPPED_DUPLICATE, result.status)
        assertEquals(emptyList(), marketPort.sellOrders)
        assertEquals(emptyList(), eventPort.submitted)
    }

    private class FakeMarketServicePort : MarketServicePort {
        val buyOrders: MutableList<PurchaseOrderDto> = mutableListOf()
        val sellOrders: MutableList<SellingOrderDto> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            buyOrders += order
            return BrokerOrderSubmissionDto(externalOrderId = "broker-buy-${buyOrders.size}")
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            sellOrders += order
            return BrokerOrderSubmissionDto(externalOrderId = "broker-sell-${sellOrders.size}")
        }

        override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
            return emptyList()
        }
    }

    private class FakeProcessedEventPort(
        private val startResult: Boolean = true,
    ) : ProcessedEventPort {
        val succeeded: MutableList<UUID> = mutableListOf()

        override suspend fun tryStart(eventId: UUID, idempotencyKey: String): Boolean {
            return startResult
        }

        override suspend fun markSuccess(eventId: UUID) {
            succeeded += eventId
        }

        override suspend fun markFailed(eventId: UUID, reason: String?) = Unit
    }

    private class FakeOrderIntentSubmissionPort : OrderIntentSubmissionPort {
        val saved: MutableList<OrderIntentSubmissionDto> = mutableListOf()

        override suspend fun saveSubmitted(submission: OrderIntentSubmissionDto) {
            saved += submission
        }

        override suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionDto? {
            return saved.firstOrNull { it.externalOrderId == externalOrderId }
        }
    }

    private class FakeOrderExecutionEventPort : OrderExecutionEventPort {
        val submitted: MutableList<OrderSubmittedMessage> = mutableListOf()
        val rejected: MutableList<OrderRejectedMessage> = mutableListOf()

        override suspend fun publishSubmitted(event: OrderSubmittedMessage) {
            submitted += event
        }

        override suspend fun publishRejected(event: OrderRejectedMessage) {
            rejected += event
        }

        override suspend fun publishFilled(event: OrderFilledMessage) = Unit
    }
}

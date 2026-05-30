package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.ProcessedEventPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
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
        val service = SubmitOrderIntentService(marketPort, processedEventPort)
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
        with(marketPort.buyOrders.single()) {
            assertEquals("TQQQ", stockId)
            assertEquals(112.0, purchasePrice)
            assertEquals(3, quantity)
        }
    }

    @Test
    fun `skips duplicate order intent`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort(startResult = false)
        val service = SubmitOrderIntentService(marketPort, processedEventPort)

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
    }

    private class FakeMarketServicePort : MarketServicePort {
        val buyOrders: MutableList<PurchaseOrderDto> = mutableListOf()
        val sellOrders: MutableList<SellingOrderDto> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto) {
            buyOrders += order
        }

        override fun sellStock(order: SellingOrderDto) {
            sellOrders += order
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
}

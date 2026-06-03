package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentResult
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentUseCase
import com.example.stockpurchaseservice.domain.ExternalOrderId
import com.example.stockpurchaseservice.domain.Money
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.OrderId
import com.example.stockpurchaseservice.domain.OrderState
import com.example.stockpurchaseservice.domain.PurchaseOrder
import com.example.stockpurchaseservice.domain.StockId
import com.example.stockpurchaseservice.domain.StrategyType
import com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class StockTradeOrchestrationServicesTest {

    @Test
    fun `legacy completed purchase submits sell order intent with persisted holding quantity`() = runBlocking {
        val purchaseOrder = PurchaseOrder(
            id = OrderId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
            strategyId = "final-price:TQQQ",
            stockId = StockId("TQQQ"),
            stockName = "ProShares UltraPro QQQ",
            requestedAt = ZonedDateTime.parse("2026-05-20T09:00:00+09:00"),
            strategyType = StrategyType.FinalPriceBatingV1,
            purchasePrice = Money(100.0),
            purchasedAt = ZonedDateTime.parse("2026-05-21T09:00:00+09:00"),
            quantity = 7,
            orderState = OrderState.PURCHASE_COMPLETED,
        )
        val repository = FakeStockOrderRepository(listOf(purchaseOrder))
        val submitOrderIntent = FakeSubmitOrderIntentUseCase(
            result = SubmitOrderIntentResult(
                status = OrderIntentSubmissionStatus.SUBMITTED,
                externalOrderId = "broker-sell-1",
            ),
        )
        val service = CreateSellOrdersByStrategyService(repository, submitOrderIntent)

        service.execute()

        val command = submitOrderIntent.commands.single()
        assertEquals(OrderIntentSide.SELL, command.side)
        assertEquals(OrderIntentType.LIMIT, command.orderType)
        assertEquals("TQQQ", command.symbol)
        assertEquals(7, command.quantity)
        assertEquals("FINAL_PRICE_BATING_V1_SELL", command.orderTag)
        assertEquals("final-price:TQQQ", command.strategyExecutionId)
        assertEquals(
            listOf(OrderState.SELLING_WAITING),
            repository.savedStates,
        )
        assertEquals(
            listOf(OrderState.SELLING_IN_PROCESS to "broker-sell-1"),
            repository.savedExternalStates,
        )
    }

    @Test
    fun `legacy sell order stays submission unknown when submitted result has no broker order id`() = runBlocking {
        val purchaseOrder = PurchaseOrder(
            id = OrderId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
            strategyId = "final-price:TQQQ",
            stockId = StockId("TQQQ"),
            stockName = "ProShares UltraPro QQQ",
            requestedAt = ZonedDateTime.parse("2026-05-20T09:00:00+09:00"),
            strategyType = StrategyType.FinalPriceBatingV1,
            purchasePrice = Money(100.0),
            purchasedAt = ZonedDateTime.parse("2026-05-21T09:00:00+09:00"),
            quantity = 7,
            orderState = OrderState.PURCHASE_COMPLETED,
        )
        val repository = FakeStockOrderRepository(listOf(purchaseOrder))
        val submitOrderIntent = FakeSubmitOrderIntentUseCase(
            result = SubmitOrderIntentResult(
                status = OrderIntentSubmissionStatus.SUBMITTED,
                externalOrderId = null,
            ),
        )
        val service = CreateSellOrdersByStrategyService(repository, submitOrderIntent)

        service.execute()

        assertEquals(
            listOf(OrderState.SELLING_WAITING, OrderState.SUBMISSION_UNKNOWN),
            repository.savedStates,
        )
        assertEquals(emptyList(), repository.savedExternalStates)
    }

    private class FakeSubmitOrderIntentUseCase(
        private val result: SubmitOrderIntentResult,
    ) : SubmitOrderIntentUseCase {
        val commands: MutableList<SubmitOrderIntentCommand> = mutableListOf()

        override suspend fun execute(command: SubmitOrderIntentCommand): SubmitOrderIntentResult {
            commands += command
            return result
        }
    }

    private class FakeStockOrderRepository(
        private val notCompletedOrders: List<Order>,
    ) : StockOrderRepository {
        val savedStates: MutableList<OrderState> = mutableListOf()
        val savedExternalStates: MutableList<Pair<OrderState, String>> = mutableListOf()

        override fun findById(id: OrderId): Order? = null

        override suspend fun save(order: Order) {
            savedStates += order.orderState
        }

        override suspend fun save(order: Order, externalOrderId: ExternalOrderId) {
            savedExternalStates += order.orderState to externalOrderId.value
        }

        override suspend fun existsByStrategyId(strategyId: String): Boolean = false

        override suspend fun findAllNotCompleted(): List<Order> = notCompletedOrders

        override suspend fun findAllWithPurchaseWaiting(): List<Order> = emptyList()

        override suspend fun findByStockIdAndQuantity(stockId: StockId, quantity: Int): Order? = null

        override suspend fun findByExternalOrderId(externalOrderId: ExternalOrderId): Order? = null
    }
}

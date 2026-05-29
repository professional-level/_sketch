package com.example.stockpurchaseservice.domain

import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderStateTest {

    @Test
    fun `valid purchase transition changes order state`() {
        val order = purchaseOrder()

        order.changeOrderState(OrderState.PURCHASE_IN_PROCESS)

        assertEquals(OrderState.PURCHASE_IN_PROCESS, order.orderState)
    }

    @Test
    fun `invalid completed order transition is rejected`() {
        val order = purchaseOrder(orderState = OrderState.SELLING_COMPLETED)

        assertFailsWith<IllegalArgumentException> {
            order.changeOrderState(OrderState.SELLING_WAITING)
        }
    }

    @Test
    fun `submission unknown can be reconciled to a concrete state`() {
        assertTrue(OrderState.SUBMISSION_UNKNOWN.canTransitionTo(OrderState.PURCHASE_COMPLETED))
        assertTrue(OrderState.SUBMISSION_UNKNOWN.canTransitionTo(OrderState.SELLING_IN_PROCESS))
        assertFalse(OrderState.SELLING_COMPLETED.canTransitionTo(OrderState.SUBMISSION_UNKNOWN))
    }

    private fun purchaseOrder(
        orderState: OrderState = OrderState.PURCHASE_WAITING,
    ): PurchaseOrder {
        return PurchaseOrder(
            strategyId = "FinalPriceBatingV1:005930",
            stockId = StockId("005930"),
            stockName = "Samsung Electronics",
            requestedAt = ZonedDateTime.now(),
            strategyType = StrategyType.FinalPriceBatingV1,
            purchasePrice = Money(70000.0),
            purchasedAt = null,
            quantity = 10,
            orderState = orderState,
        )
    }
}

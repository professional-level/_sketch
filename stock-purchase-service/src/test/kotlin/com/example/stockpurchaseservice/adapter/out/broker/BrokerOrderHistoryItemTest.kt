package com.example.stockpurchaseservice.adapter.out.broker

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.ExecutionQuantityModeDto
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class BrokerOrderHistoryItemTest {

    @Test
    fun `maps cumulative filled row to execution dto`() {
        val dto = row(
            cumulativeFilledQuantity = 3,
            side = OrderIntentSide.BUY,
            averageExecutionPrice = 112.5,
        ).toExecutionDto()

        checkNotNull(dto)
        assertEquals("TQQQ", dto.stockId)
        assertEquals(3, dto.quantity)
        assertEquals(ExecutionTypeDto.PURCHASE, dto.type)
        assertEquals(ExecutionQuantityModeDto.CUMULATIVE, dto.quantityMode)
        assertEquals(112.5, dto.averageExecutionPrice)
    }

    @Test
    fun `maps cancelled row to cancelled status`() {
        val status = listOf(
            row(cancelledQuantity = 3, cancelled = true),
        ).findStatusFor(query(externalOrderId = "broker-1"))

        assertEquals(BrokerOrderStatus.CANCELLED, status.status)
        assertEquals("broker-1", status.externalOrderId)
    }

    @Test
    fun `maps partially filled row to partially filled status`() {
        val status = listOf(
            row(cumulativeFilledQuantity = 1, orderedPrice = 112.5),
        ).findStatusFor(query(externalOrderId = "broker-1"))

        assertEquals(BrokerOrderStatus.PARTIALLY_FILLED, status.status)
        assertEquals(3L, status.orderedQuantity)
        assertEquals(112.5, status.orderedPrice)
        assertEquals(1L, status.cumulativeFilledQuantity)
        assertEquals(2L, status.remainingQuantity)
        assertEquals(ORDERED_AT, status.brokerReportedAt)
    }

    @Test
    fun `maps filled row to filled status`() {
        val status = listOf(
            row(cumulativeFilledQuantity = 3),
        ).findStatusFor(query(externalOrderId = "broker-1"))

        assertEquals(BrokerOrderStatus.FILLED, status.status)
        assertEquals(3L, status.orderedQuantity)
        assertEquals(3L, status.cumulativeFilledQuantity)
        assertEquals(0L, status.remainingQuantity)
    }

    @Test
    fun `prefers terminal cancel row matched by original broker order id`() {
        val status = listOf(
            row(externalOrderId = "broker-1"),
            row(
                externalOrderId = "cancel-broker-1",
                originalOrderId = "broker-1",
                cancelledQuantity = 3,
                cancelled = true,
            ),
        ).findStatusFor(query(externalOrderId = "broker-1"))

        assertEquals(BrokerOrderStatus.CANCELLED, status.status)
        assertEquals("broker-1", status.externalOrderId)
    }

    @Test
    fun `returns unknown when order lookup is ambiguous without broker order id`() {
        val status = listOf(
            row(externalOrderId = "broker-1"),
            row(externalOrderId = "broker-2"),
        ).findStatusFor(query(externalOrderId = null))

        assertEquals(BrokerOrderStatus.UNKNOWN, status.status)
    }

    @Test
    fun `uses ordered quantity to disambiguate lookup without broker order id`() {
        val status = listOf(
            row(externalOrderId = "broker-1", orderedQuantity = 1, cumulativeFilledQuantity = 1),
            row(externalOrderId = "broker-2", orderedQuantity = 3, cumulativeFilledQuantity = 3),
        ).findStatusFor(query(externalOrderId = null, orderedQuantity = 3))

        assertEquals(BrokerOrderStatus.FILLED, status.status)
        assertEquals("broker-2", status.externalOrderId)
        assertEquals(3L, status.orderedQuantity)
        assertEquals(3L, status.cumulativeFilledQuantity)
    }

    @Test
    fun `keeps broader candidates when broker rows do not expose matching ordered quantity`() {
        val status = listOf(
            row(externalOrderId = "broker-1", orderedQuantity = 0),
        ).findStatusFor(query(externalOrderId = null, orderedQuantity = 3))

        assertEquals(BrokerOrderStatus.SUBMITTED, status.status)
        assertEquals("broker-1", status.externalOrderId)
    }

    @Test
    fun `uses submitted price to disambiguate lookup without broker order id`() {
        val status = listOf(
            row(externalOrderId = "broker-1", orderedPrice = 111.0),
            row(externalOrderId = "broker-2", orderedPrice = 112.5),
        ).findStatusFor(query(externalOrderId = null, orderedQuantity = 3, submittedPrice = 112.5))

        assertEquals(BrokerOrderStatus.SUBMITTED, status.status)
        assertEquals("broker-2", status.externalOrderId)
        assertEquals(112.5, status.orderedPrice)
    }

    @Test
    fun `keeps quantity candidates when broker rows do not expose matching ordered price`() {
        val status = listOf(
            row(externalOrderId = "broker-1", orderedPrice = null),
        ).findStatusFor(query(externalOrderId = null, orderedQuantity = 3, submittedPrice = 112.5))

        assertEquals(BrokerOrderStatus.SUBMITTED, status.status)
        assertEquals("broker-1", status.externalOrderId)
    }

    private fun query(
        externalOrderId: String?,
        orderedQuantity: Long? = null,
        submittedPrice: Double? = null,
    ): BrokerOrderStatusQuery {
        return BrokerOrderStatusQuery(
            orderIntentId = UUID.randomUUID(),
            internalOrderId = UUID.randomUUID(),
            externalOrderId = externalOrderId,
            symbol = "TQQQ",
            side = OrderIntentSide.BUY,
            orderedQuantity = orderedQuantity,
            submittedPrice = submittedPrice,
            market = StockOrderMarket.OVERSEAS_US,
            submittedAt = ORDERED_AT,
        )
    }

    private fun row(
        externalOrderId: String = "broker-1",
        originalOrderId: String? = null,
        cumulativeFilledQuantity: Long = 0,
        cancelledQuantity: Long = 0,
        cancelled: Boolean = false,
        side: OrderIntentSide? = OrderIntentSide.BUY,
        averageExecutionPrice: Double? = null,
        orderedQuantity: Long = 3,
        orderedPrice: Double? = null,
    ): BrokerOrderHistoryItem {
        return BrokerOrderHistoryItem(
            externalOrderId = externalOrderId,
            originalOrderId = originalOrderId,
            branchOrderNumber = "00001",
            symbol = "TQQQ",
            stockName = "TQQQ",
            orderedAt = ORDERED_AT,
            orderedQuantity = orderedQuantity,
            orderedPrice = orderedPrice,
            cumulativeFilledQuantity = cumulativeFilledQuantity,
            remainingQuantity = orderedQuantity - cumulativeFilledQuantity,
            rejectedQuantity = 0,
            cancelledQuantity = cancelledQuantity,
            cancelled = cancelled,
            side = side,
            averageExecutionPrice = averageExecutionPrice,
        )
    }

    companion object {
        private val ORDERED_AT = ZonedDateTime.parse("2026-06-02T09:00:00+09:00")
    }
}

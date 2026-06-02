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
        assertEquals("broker-1:3:PURCHASE", dto.externalExecutionId)
        assertEquals(ExecutionQuantityModeDto.CUMULATIVE, dto.quantityMode)
        assertEquals(112.5, dto.averageExecutionPrice)
    }

    @Test
    fun `uses broker execution id when present`() {
        val dto = row(
            cumulativeFilledQuantity = 3,
            externalExecutionId = "broker-fill-1",
        ).toExecutionDto()

        checkNotNull(dto)
        assertEquals("broker-fill-1", dto.externalExecutionId)
        assertEquals(ExecutionQuantityModeDto.CUMULATIVE, dto.quantityMode)
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
            row(
                cumulativeFilledQuantity = 1,
                externalExecutionId = "broker-fill-1",
                orderedPrice = 112.5,
            ),
        ).findStatusFor(query(externalOrderId = "broker-1"))

        assertEquals(BrokerOrderStatus.PARTIALLY_FILLED, status.status)
        assertEquals("broker-fill-1", status.externalExecutionId)
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
        assertEquals("broker-1:3:PURCHASE", status.externalExecutionId)
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
    fun `does not treat rejected cancel row as original order rejection`() {
        val status = listOf(
            row(externalOrderId = "broker-1"),
            row(
                externalOrderId = "cancel-broker-1",
                originalOrderId = "broker-1",
                rejectionReason = "cancel request rejected",
            ),
        ).findStatusFor(query(externalOrderId = "broker-1"))

        assertEquals(BrokerOrderStatus.SUBMITTED, status.status)
        assertEquals("broker-1", status.externalOrderId)
        assertEquals(null, status.reason)
    }

    @Test
    fun `returns unknown when only rejected linked row matches original order id`() {
        val status = listOf(
            row(
                externalOrderId = "cancel-broker-1",
                originalOrderId = "broker-1",
                rejectionReason = "cancel request rejected",
            ),
        ).findStatusFor(query(externalOrderId = "broker-1"))

        assertEquals(BrokerOrderStatus.UNKNOWN, status.status)
        assertEquals("broker-1", status.externalOrderId)
    }

    @Test
    fun `uses branch order number to disambiguate broker order id matches`() {
        val status = listOf(
            row(externalOrderId = "broker-1", branchOrderNumber = "00001", orderedQuantity = 1),
            row(externalOrderId = "broker-1", branchOrderNumber = "00002", orderedQuantity = 3),
        ).findStatusFor(query(externalOrderId = "broker-1", branchOrderNumber = "00002"))

        assertEquals(BrokerOrderStatus.SUBMITTED, status.status)
        assertEquals("broker-1", status.externalOrderId)
        assertEquals(3L, status.orderedQuantity)
    }

    @Test
    fun `keeps broader candidates when broker rows do not expose matching branch order number`() {
        val status = listOf(
            row(externalOrderId = "broker-1", branchOrderNumber = null),
        ).findStatusFor(query(externalOrderId = "broker-1", branchOrderNumber = "00002"))

        assertEquals(BrokerOrderStatus.SUBMITTED, status.status)
        assertEquals("broker-1", status.externalOrderId)
    }

    @Test
    fun `returns unknown when explicit branch order number does not match broker rows`() {
        val status = listOf(
            row(externalOrderId = "broker-1", branchOrderNumber = "00001"),
        ).findStatusFor(query(externalOrderId = "broker-1", branchOrderNumber = "00002"))

        assertEquals(BrokerOrderStatus.UNKNOWN, status.status)
        assertEquals("broker-1", status.externalOrderId)
        assertEquals("broker order not found", status.reason)
    }

    @Test
    fun `preserves original fill details when terminal cancel row has no fill quantity`() {
        val status = listOf(
            row(
                externalOrderId = "broker-1",
                externalExecutionId = "broker-fill-1",
                cumulativeFilledQuantity = 1,
                averageExecutionPrice = 112.5,
                orderedPrice = 112.0,
            ),
            row(
                externalOrderId = "cancel-broker-1",
                originalOrderId = "broker-1",
                cancelledQuantity = 2,
                cancelled = true,
            ),
        ).findStatusFor(query(externalOrderId = "broker-1"))

        assertEquals(BrokerOrderStatus.CANCELLED, status.status)
        assertEquals("broker-1", status.externalOrderId)
        assertEquals("broker-fill-1", status.externalExecutionId)
        assertEquals(1L, status.cumulativeFilledQuantity)
        assertEquals(112.5, status.averageExecutionPrice)
        assertEquals(112.0, status.orderedPrice)
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
    fun `returns unknown when broker id is absent and only one ambiguous candidate is terminal`() {
        val status = listOf(
            row(externalOrderId = "broker-1", cumulativeFilledQuantity = 3),
            row(externalOrderId = "broker-2"),
        ).findStatusFor(query(externalOrderId = null, orderedQuantity = 3))

        assertEquals(BrokerOrderStatus.UNKNOWN, status.status)
        assertEquals("ambiguous broker orders: broker-1, broker-2", status.reason)
    }

    @Test
    fun `recovers unknown submission from multiple rows for same broker order id`() {
        val status = listOf(
            row(
                externalOrderId = "broker-1",
                externalExecutionId = "fill-1",
                cumulativeFilledQuantity = 1,
                orderedQuantity = 3,
                orderedPrice = 112.5,
            ),
            row(
                externalOrderId = "broker-1",
                externalExecutionId = "fill-2",
                cumulativeFilledQuantity = 2,
                orderedQuantity = 3,
                orderedPrice = 112.5,
            ),
        ).findStatusFor(query(externalOrderId = null, orderedQuantity = 3, submittedPrice = 112.5))

        assertEquals(BrokerOrderStatus.PARTIALLY_FILLED, status.status)
        assertEquals("broker-1", status.externalOrderId)
        assertEquals("fill-2", status.externalExecutionId)
        assertEquals(2L, status.cumulativeFilledQuantity)
        assertEquals(1L, status.remainingQuantity)
    }

    @Test
    fun `recovers unknown submission from linked original and cancel rows`() {
        val status = listOf(
            row(
                externalOrderId = "broker-1",
                cumulativeFilledQuantity = 1,
                orderedQuantity = 3,
                orderedPrice = 112.5,
            ),
            row(
                externalOrderId = "cancel-broker-1",
                originalOrderId = "broker-1",
                cancelledQuantity = 2,
                cancelled = true,
                orderedQuantity = 3,
                orderedPrice = 112.5,
            ),
        ).findStatusFor(query(externalOrderId = null, orderedQuantity = 3, submittedPrice = 112.5))

        assertEquals(BrokerOrderStatus.CANCELLED, status.status)
        assertEquals("cancel-broker-1", status.externalOrderId)
        assertEquals(1L, status.cumulativeFilledQuantity)
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
    fun `uses unknown side row when broker id is absent and identity is unambiguous`() {
        val status = listOf(
            row(externalOrderId = "broker-1", side = null, orderedQuantity = 3, orderedPrice = 112.5),
        ).findStatusFor(query(externalOrderId = null, orderedQuantity = 3, submittedPrice = 112.5))

        assertEquals(BrokerOrderStatus.SUBMITTED, status.status)
        assertEquals("broker-1", status.externalOrderId)
        assertEquals(3L, status.orderedQuantity)
        assertEquals(112.5, status.orderedPrice)
    }

    @Test
    fun `does not use opposite side row when broker id is absent`() {
        val status = listOf(
            row(externalOrderId = "broker-1", side = OrderIntentSide.SELL, orderedQuantity = 3, orderedPrice = 112.5),
        ).findStatusFor(query(externalOrderId = null, orderedQuantity = 3, submittedPrice = 112.5))

        assertEquals(BrokerOrderStatus.UNKNOWN, status.status)
        assertEquals("broker order not found", status.reason)
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

    @Test
    fun `matches broker date when submitted timestamp uses another timezone`() {
        val status = listOf(
            row(
                externalOrderId = "broker-1",
                orderedAt = ZonedDateTime.parse("2026-06-02T00:10:00+09:00"),
                orderedQuantity = 3,
                orderedPrice = 112.5,
            ),
        ).findStatusFor(
            query(
                externalOrderId = null,
                orderedQuantity = 3,
                submittedPrice = 112.5,
                submittedAt = ZonedDateTime.parse("2026-06-01T15:10:00Z"),
            ),
        )

        assertEquals(BrokerOrderStatus.SUBMITTED, status.status)
        assertEquals("broker-1", status.externalOrderId)
    }

    private fun query(
        externalOrderId: String?,
        branchOrderNumber: String? = null,
        orderedQuantity: Long? = null,
        submittedPrice: Double? = null,
        submittedAt: ZonedDateTime = ORDERED_AT,
    ): BrokerOrderStatusQuery {
        return BrokerOrderStatusQuery(
            orderIntentId = UUID.randomUUID(),
            internalOrderId = UUID.randomUUID(),
            externalOrderId = externalOrderId,
            branchOrderNumber = branchOrderNumber,
            symbol = "TQQQ",
            side = OrderIntentSide.BUY,
            orderedQuantity = orderedQuantity,
            submittedPrice = submittedPrice,
            market = StockOrderMarket.OVERSEAS_US,
            submittedAt = submittedAt,
        )
    }

    private fun row(
        externalOrderId: String = "broker-1",
        externalExecutionId: String? = null,
        originalOrderId: String? = null,
        cumulativeFilledQuantity: Long = 0,
        cancelledQuantity: Long = 0,
        cancelled: Boolean = false,
        side: OrderIntentSide? = OrderIntentSide.BUY,
        averageExecutionPrice: Double? = null,
        orderedQuantity: Long = 3,
        orderedPrice: Double? = null,
        branchOrderNumber: String? = "00001",
        rejectedQuantity: Long = 0,
        rejectionReason: String? = null,
        statusMessage: String? = null,
        orderedAt: ZonedDateTime = ORDERED_AT,
    ): BrokerOrderHistoryItem {
        return BrokerOrderHistoryItem(
            externalOrderId = externalOrderId,
            externalExecutionId = externalExecutionId,
            originalOrderId = originalOrderId,
            branchOrderNumber = branchOrderNumber,
            symbol = "TQQQ",
            stockName = "TQQQ",
            orderedAt = orderedAt,
            orderedQuantity = orderedQuantity,
            orderedPrice = orderedPrice,
            cumulativeFilledQuantity = cumulativeFilledQuantity,
            remainingQuantity = orderedQuantity - cumulativeFilledQuantity,
            rejectedQuantity = rejectedQuantity,
            cancelledQuantity = cancelledQuantity,
            cancelled = cancelled,
            side = side,
            averageExecutionPrice = averageExecutionPrice,
            statusMessage = statusMessage,
            rejectionReason = rejectionReason,
        )
    }

    companion object {
        private val ORDERED_AT = ZonedDateTime.parse("2026-06-02T09:00:00+09:00")
    }
}

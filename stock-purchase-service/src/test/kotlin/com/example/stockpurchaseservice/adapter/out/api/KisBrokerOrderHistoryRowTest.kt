package com.example.stockpurchaseservice.adapter.out.api

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

class KisBrokerOrderHistoryRowTest {

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
    fun `returns unknown when order lookup is ambiguous without broker order id`() {
        val status = listOf(
            row(externalOrderId = "broker-1"),
            row(externalOrderId = "broker-2"),
        ).findStatusFor(query(externalOrderId = null))

        assertEquals(BrokerOrderStatus.UNKNOWN, status.status)
    }

    private fun query(externalOrderId: String?): BrokerOrderStatusQuery {
        return BrokerOrderStatusQuery(
            orderIntentId = UUID.randomUUID(),
            internalOrderId = UUID.randomUUID(),
            externalOrderId = externalOrderId,
            symbol = "TQQQ",
            side = OrderIntentSide.BUY,
            market = StockOrderMarket.OVERSEAS_US,
            submittedAt = ORDERED_AT,
        )
    }

    private fun row(
        externalOrderId: String = "broker-1",
        cumulativeFilledQuantity: Long = 0,
        cancelledQuantity: Long = 0,
        cancelled: Boolean = false,
        side: OrderIntentSide? = OrderIntentSide.BUY,
        averageExecutionPrice: Double? = null,
    ): KisBrokerOrderHistoryRow {
        return KisBrokerOrderHistoryRow(
            externalOrderId = externalOrderId,
            branchOrderNumber = "00001",
            symbol = "TQQQ",
            stockName = "TQQQ",
            orderedAt = ORDERED_AT,
            orderedQuantity = 3,
            cumulativeFilledQuantity = cumulativeFilledQuantity,
            remainingQuantity = 3 - cumulativeFilledQuantity,
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

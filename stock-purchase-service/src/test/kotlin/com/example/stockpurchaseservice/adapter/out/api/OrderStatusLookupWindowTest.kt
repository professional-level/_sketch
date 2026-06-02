package com.example.stockpurchaseservice.adapter.out.api

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderStatusLookupWindowTest {

    @Test
    fun `normalizes status lookup window to broker timezone`() {
        val query = BrokerOrderStatusQuery(
            orderIntentId = UUID.randomUUID(),
            internalOrderId = UUID.randomUUID(),
            externalOrderId = null,
            symbol = "TQQQ",
            side = OrderIntentSide.BUY,
            market = StockOrderMarket.OVERSEAS_US,
            submittedAt = ZonedDateTime.parse("2026-06-01T15:10:00Z"),
        )

        val window = query.toBrokerOrderHistoryLookupWindow(
            backfillDays = 0,
            forwardDays = 0,
            fallbackNow = ZonedDateTime.parse("2026-06-01T00:00:00Z"),
        )

        assertEquals(ZoneId.of("Asia/Seoul"), window.from.zone)
        assertEquals("2026-06-02", window.from.toLocalDate().toString())
        assertEquals("2026-06-02", window.to.toLocalDate().toString())
    }
}

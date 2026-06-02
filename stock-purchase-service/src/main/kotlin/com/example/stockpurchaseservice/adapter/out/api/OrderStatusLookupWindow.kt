package com.example.stockpurchaseservice.adapter.out.api

import com.example.stockpurchaseservice.adapter.out.broker.BROKER_ORDER_ZONE
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import java.time.ZonedDateTime

internal data class BrokerOrderHistoryLookupWindow(
    val from: ZonedDateTime,
    val to: ZonedDateTime,
)

internal fun BrokerOrderStatusQuery.toBrokerOrderHistoryLookupWindow(
    backfillDays: Long,
    forwardDays: Long,
    fallbackNow: ZonedDateTime = ZonedDateTime.now(BROKER_ORDER_ZONE),
): BrokerOrderHistoryLookupWindow {
    val anchor = (submittedAt ?: fallbackNow).withZoneSameInstant(BROKER_ORDER_ZONE)
    return BrokerOrderHistoryLookupWindow(
        from = anchor.minusDays(backfillDays.coerceAtLeast(0)),
        to = anchor.plusDays(forwardDays.coerceAtLeast(0)),
    )
}

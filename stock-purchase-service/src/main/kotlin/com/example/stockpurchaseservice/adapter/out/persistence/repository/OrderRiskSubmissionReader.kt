package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionSide
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionMarket
import java.time.ZonedDateTime

internal interface OrderRiskSubmissionReader {
    suspend fun countBrokerSubmittedBetween(
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Long

    suspend fun existsActiveDuplicate(
        strategyExecutionId: String,
        symbol: String,
        side: OrderIntentSubmissionSide,
        orderTag: String,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Boolean

    suspend fun sumActiveBuyNotional(market: OrderIntentSubmissionMarket): Double
}

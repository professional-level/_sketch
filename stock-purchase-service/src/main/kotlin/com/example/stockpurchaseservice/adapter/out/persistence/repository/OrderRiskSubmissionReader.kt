package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionSide
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionMarket
import java.time.ZonedDateTime

internal interface OrderRiskSubmissionReader {
    suspend fun countBrokerSubmittedBetween(
        market: OrderIntentSubmissionMarket,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Long

    suspend fun existsActiveDuplicate(
        market: OrderIntentSubmissionMarket,
        strategyExecutionId: String,
        symbol: String,
        side: OrderIntentSubmissionSide,
        orderTag: String,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Boolean

    suspend fun sumActiveBuyNotional(market: OrderIntentSubmissionMarket): Double

    suspend fun sumActiveSellQuantity(
        symbol: String,
        market: OrderIntentSubmissionMarket,
    ): Long
}

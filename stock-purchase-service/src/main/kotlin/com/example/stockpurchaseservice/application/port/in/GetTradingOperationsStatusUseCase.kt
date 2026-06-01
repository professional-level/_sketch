package com.example.stockpurchaseservice.application.port.`in`

import com.example.common.UseCase
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusSnapshot
import java.time.ZonedDateTime

@UseCase
interface GetTradingOperationsStatusUseCase {
    suspend fun execute(): TradingOperationsStatusResult
}

data class TradingOperationsStatusResult(
    val generatedAt: ZonedDateTime,
    val snapshot: TradingOperationsStatusSnapshot,
)

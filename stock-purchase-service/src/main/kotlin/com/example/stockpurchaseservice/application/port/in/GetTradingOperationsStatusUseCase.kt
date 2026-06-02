package com.example.stockpurchaseservice.application.port.`in`

import com.example.common.UseCase
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusSnapshot
import java.time.ZonedDateTime

@UseCase
interface GetTradingOperationsStatusUseCase {
    suspend fun execute(): TradingOperationsStatusResult
}

data class TradingOperationsStatusResult(
    val generatedAt: ZonedDateTime,
    val snapshot: TradingOperationsStatusSnapshot,
    val accountSnapshots: List<TradingAccountSnapshotStatus> = emptyList(),
)

data class TradingAccountSnapshotStatus(
    val market: StockOrderMarket,
    val exchange: String,
    val currency: String,
    val available: Boolean,
    val reason: String? = null,
    val availableCashAmount: Double? = null,
    val cashCurrency: String? = null,
    val orderableCashAmount: Double? = null,
    val settledCashAmount: Double? = null,
    val withdrawableCashAmount: Double? = null,
    val totalPurchaseAmount: Double? = null,
    val totalEvaluationAmount: Double? = null,
    val totalProfitLossAmount: Double? = null,
    val positions: List<TradingAccountPositionStatus> = emptyList(),
)

data class TradingAccountPositionStatus(
    val symbol: String,
    val stockName: String,
    val quantity: Long,
    val averagePurchasePrice: Double? = null,
    val currentPrice: Double? = null,
    val purchaseAmount: Double? = null,
    val evaluationAmount: Double? = null,
    val profitLossAmount: Double? = null,
)

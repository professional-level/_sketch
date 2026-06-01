package com.example.stockpurchaseservice.adapter.`in`.web

import com.example.common.WebAdapter
import com.example.stockpurchaseservice.application.port.`in`.BrokerAccountSnapshotResult
import com.example.stockpurchaseservice.application.port.`in`.GetBrokerAccountSnapshotCommand
import com.example.stockpurchaseservice.application.port.`in`.GetBrokerAccountSnapshotUseCase
import com.example.stockpurchaseservice.application.port.out.AccountPositionSnapshotDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.ZonedDateTime

@WebAdapter
@RequestMapping("/operations/trading/account-snapshot")
internal class BrokerAccountSnapshotController(
    private val getBrokerAccountSnapshotUseCase: GetBrokerAccountSnapshotUseCase,
) {

    @GetMapping
    fun accountSnapshot(
        @RequestParam(defaultValue = "OVERSEAS_US") market: StockOrderMarket,
        @RequestParam(defaultValue = "NASD") exchange: String,
        @RequestParam(defaultValue = "USD") currency: String,
    ): BrokerAccountSnapshotResponse {
        return getBrokerAccountSnapshotUseCase.execute(
            GetBrokerAccountSnapshotCommand(
                market = market,
                exchange = exchange,
                currency = currency,
            ),
        ).toResponse()
    }
}

internal data class BrokerAccountSnapshotResponse(
    val generatedAt: ZonedDateTime,
    val market: StockOrderMarket,
    val exchange: String,
    val currency: String,
    val positions: List<AccountPositionSnapshotDto>,
    val availableCashAmount: Double?,
    val totalPurchaseAmount: Double?,
    val totalEvaluationAmount: Double?,
    val totalProfitLossAmount: Double?,
)

private fun BrokerAccountSnapshotResult.toResponse(): BrokerAccountSnapshotResponse {
    return BrokerAccountSnapshotResponse(
        generatedAt = generatedAt,
        market = snapshot.market,
        exchange = snapshot.exchange,
        currency = snapshot.currency,
        positions = snapshot.positions,
        availableCashAmount = snapshot.availableCashAmount,
        totalPurchaseAmount = snapshot.totalPurchaseAmount,
        totalEvaluationAmount = snapshot.totalEvaluationAmount,
        totalProfitLossAmount = snapshot.totalProfitLossAmount,
    )
}

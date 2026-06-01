package com.example.stockpurchaseservice.application.port.`in`

import com.example.common.UseCase
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import java.time.ZonedDateTime

@UseCase
interface GetBrokerAccountSnapshotUseCase {
    fun execute(command: GetBrokerAccountSnapshotCommand): BrokerAccountSnapshotResult
}

data class GetBrokerAccountSnapshotCommand(
    val market: StockOrderMarket,
    val exchange: String = "NASD",
    val currency: String = "USD",
)

data class BrokerAccountSnapshotResult(
    val generatedAt: ZonedDateTime,
    val snapshot: AccountSnapshotDto,
)

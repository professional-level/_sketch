package com.example.stockpurchaseservice.application.service

import com.example.common.UseCaseImpl
import com.example.stockpurchaseservice.application.port.`in`.BrokerAccountSnapshotResult
import com.example.stockpurchaseservice.application.port.`in`.GetBrokerAccountSnapshotCommand
import com.example.stockpurchaseservice.application.port.`in`.GetBrokerAccountSnapshotUseCase
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotQuery
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import java.time.Clock
import java.time.ZonedDateTime

@UseCaseImpl
class GetBrokerAccountSnapshotService(
    private val marketServicePort: MarketServicePort,
) : GetBrokerAccountSnapshotUseCase {
    internal var clock: Clock = Clock.systemDefaultZone()

    override fun execute(command: GetBrokerAccountSnapshotCommand): BrokerAccountSnapshotResult {
        return BrokerAccountSnapshotResult(
            generatedAt = ZonedDateTime.now(clock),
            snapshot = marketServicePort.findAccountSnapshot(
                AccountSnapshotQuery(
                    market = command.market,
                    exchange = command.exchange,
                    currency = command.currency,
                ),
            ),
        )
    }
}

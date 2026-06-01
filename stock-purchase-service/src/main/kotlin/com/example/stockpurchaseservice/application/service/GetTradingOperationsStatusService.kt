package com.example.stockpurchaseservice.application.service

import com.example.common.UseCaseImpl
import com.example.stockpurchaseservice.application.port.`in`.GetTradingOperationsStatusUseCase
import com.example.stockpurchaseservice.application.port.`in`.TradingOperationsStatusResult
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusPort
import java.time.Clock
import java.time.ZonedDateTime

@UseCaseImpl
class GetTradingOperationsStatusService(
    private val tradingOperationsStatusPort: TradingOperationsStatusPort,
) : GetTradingOperationsStatusUseCase {
    internal var clock: Clock = Clock.systemDefaultZone()

    override suspend fun execute(): TradingOperationsStatusResult {
        return TradingOperationsStatusResult(
            generatedAt = ZonedDateTime.now(clock),
            snapshot = tradingOperationsStatusPort.loadStatus(),
        )
    }
}

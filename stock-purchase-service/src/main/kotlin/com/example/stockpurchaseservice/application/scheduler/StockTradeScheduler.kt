package com.example.stockpurchaseservice.application.scheduler

import com.example.stockpurchaseservice.application.port.`in`.CreateSellOrdersByStrategyUseCase
import com.example.stockpurchaseservice.application.port.`in`.ReconcileExecutionsUseCase
import com.example.stockpurchaseservice.application.port.`in`.SimulateStockPurchaseUseCase
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
internal class StockTradeScheduler(
    private val createSellOrdersByStrategyUseCase: CreateSellOrdersByStrategyUseCase,
    private val reconcileExecutionsUseCase: ReconcileExecutionsUseCase,
    private val simulateStockPurchaseUseCase: SimulateStockPurchaseUseCase,
) {
    // TODO: Check market holidays/trading calendar before running trade jobs.
    @Scheduled(cron = "0 */1 * ? * MON-FRI") // TODO: 판매 전략에 따라 스케쥴 시간 변경필요
    suspend fun sellOrderByStrategies() {
        createSellOrdersByStrategyUseCase.execute()
    }

    @Scheduled(cron = "0 */1 * ? * MON-FRI") // TODO: 판매 전략에 따라 스케쥴 시간 변경필요
    suspend fun executionCheck() {
        reconcileExecutionsUseCase.execute()
    }

    @Scheduled(cron = "0 */1 * ? * MON-FRI")
    suspend fun simulateStockPurchase() {
        simulateStockPurchaseUseCase.execute()
    }
}

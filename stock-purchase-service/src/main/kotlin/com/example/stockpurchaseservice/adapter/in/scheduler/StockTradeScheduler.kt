package com.example.stockpurchaseservice.adapter.`in`.scheduler

import com.example.common.WebAdapter
import com.example.stockpurchaseservice.application.port.`in`.StockTradeScheduleUseCase
import org.springframework.retry.annotation.Recover
import org.springframework.scheduling.annotation.Scheduled

@WebAdapter
internal class StockTradeScheduler(
    private val stockTradeScheduleUseCase: StockTradeScheduleUseCase,
) {

    @Scheduled(cron = "0 */1 * ? * MON-FRI")
    suspend fun sellOrderByStrategies() {
        stockTradeScheduleUseCase.executeSellOrdersByStrategies()
    }

    @Recover
    suspend fun recover(e: Exception) {
        println("재시도 횟수를 초과했습니다: ${e.message}")
        TODO()
    }

    @Scheduled(cron = "0 */1 * ? * MON-FRI")
    suspend fun executionCheck() {
        stockTradeScheduleUseCase.executeExecutionCheck()
    }

    @Scheduled(cron = "0 10 8 ? * MON-FRI")
    fun initializeExecutionQueue() {
        stockTradeScheduleUseCase.initializeExecutionQueue()
    }
}
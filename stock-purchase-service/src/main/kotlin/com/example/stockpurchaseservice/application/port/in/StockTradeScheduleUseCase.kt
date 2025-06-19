package com.example.stockpurchaseservice.application.port.`in`

import com.example.common.UseCase

@UseCase
interface StockTradeScheduleUseCase {
    suspend fun executeSellOrdersByStrategies()
    suspend fun executeExecutionCheck()
    fun initializeExecutionQueue()
}
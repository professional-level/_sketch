package com.example.stockpurchaseservice.application.port.`in`

import com.example.common.UseCase

@UseCase
interface CreateSellOrdersByStrategyUseCase {
    suspend fun execute()
}

@UseCase
interface ReconcileExecutionsUseCase {
    suspend fun execute()
}

@UseCase
interface SimulateStockPurchaseUseCase {
    suspend fun execute()
}

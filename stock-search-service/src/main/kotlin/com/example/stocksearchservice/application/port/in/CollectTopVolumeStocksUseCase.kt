package com.example.stocksearchservice.application.port.`in`

import com.example.common.UseCase

@UseCase
interface CollectTopVolumeStocksUseCase {
    suspend fun execute(): CollectTopVolumeStocksResult
}

data class CollectTopVolumeStocksResult(
    val savedCount: Int,
)

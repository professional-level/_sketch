package com.example.stocksearchservice.application.port.`in`

import com.example.common.UseCase

@UseCase
interface DiscoverFinalPriceBatingCandidatesUseCase {
    suspend fun execute(): DiscoverFinalPriceBatingCandidatesResult
}

data class DiscoverFinalPriceBatingCandidatesResult(
    val savedCount: Int,
)

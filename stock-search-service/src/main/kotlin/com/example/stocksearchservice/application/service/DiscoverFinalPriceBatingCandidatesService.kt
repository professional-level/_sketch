package com.example.stocksearchservice.application.service

import com.example.common.UseCaseImpl
import com.example.stocksearchservice.application.port.`in`.DiscoverFinalPriceBatingCandidatesResult
import com.example.stocksearchservice.application.port.`in`.DiscoverFinalPriceBatingCandidatesUseCase
import com.example.stocksearchservice.domain.repository.FinalPriceBatingStrategyV1Repository
import com.example.stocksearchservice.domain.repository.StockInformationRepository
import com.example.stocksearchservice.domain.service.FinalPriceBatingStockAnalyzer

@UseCaseImpl
class DiscoverFinalPriceBatingCandidatesService(
    private val stockInformationRepository: StockInformationRepository,
    private val finalPriceBatingStrategyV1Repository: FinalPriceBatingStrategyV1Repository,
    private val stockAnalyzer: FinalPriceBatingStockAnalyzer,
) : DiscoverFinalPriceBatingCandidatesUseCase {

    override suspend fun execute(): DiscoverFinalPriceBatingCandidatesResult {
        val top10VolumeStockList = stockInformationRepository.findTop10VolumeStocks()
        val validStocks = stockAnalyzer.analyzeStocks(top10VolumeStockList)
        // TODO: Add rate limiting/cache for per-symbol program-volume API calls when this moves beyond sketch scale.
        val programVolumeAdaptedList = validStocks.map { entity ->
            val foreignerVolume =
                stockInformationRepository.getProgramPureBuyingVolumeAtLatestOfDay(entity.stock.stockId)
            // TODO: Decide whether missing foreigner volume should remain sentinel -1 or be modeled explicitly.
            entity.setForeignerStockVolume(foreignerVolume?.value ?: -1)
            entity
        }.filter { it.isValidProgramForeignerTradeVolume() }
            // TODO: Revisit repository call shape; per-symbol filtering is simple but may need batching.
            .filter { stockInformationRepository.isHighestProgramVolumeIn5Days(id = it.stock.stockId) }

        finalPriceBatingStrategyV1Repository.saveAll(programVolumeAdaptedList)
        return DiscoverFinalPriceBatingCandidatesResult(savedCount = programVolumeAdaptedList.size)
    }
}

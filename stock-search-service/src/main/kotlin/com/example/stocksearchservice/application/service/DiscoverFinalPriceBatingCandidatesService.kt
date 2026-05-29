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
        val programVolumeAdaptedList = validStocks.map { entity ->
            val foreignerVolume =
                stockInformationRepository.getProgramPureBuyingVolumeAtLatestOfDay(entity.stock.stockId)
            entity.setForeignerStockVolume(foreignerVolume?.value ?: -1)
            entity
        }.filter { it.isValidProgramForeignerTradeVolume() }
            .filter { stockInformationRepository.isHighestProgramVolumeIn5Days(id = it.stock.stockId) }

        finalPriceBatingStrategyV1Repository.saveAll(programVolumeAdaptedList)
        return DiscoverFinalPriceBatingCandidatesResult(savedCount = programVolumeAdaptedList.size)
    }
}

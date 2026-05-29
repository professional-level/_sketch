package com.example.stocksearchservice.application.service

import com.example.common.UseCaseImpl
import com.example.stocksearchservice.application.port.`in`.CollectTopVolumeStocksResult
import com.example.stocksearchservice.application.port.`in`.CollectTopVolumeStocksUseCase
import com.example.stocksearchservice.domain.StockLog
import com.example.stocksearchservice.domain.repository.StockInformationRepository

@UseCaseImpl
class CollectTopVolumeStocksService(
    private val stockInformationRepository: StockInformationRepository,
) : CollectTopVolumeStocksUseCase {

    override suspend fun execute(): CollectTopVolumeStocksResult {
        val stockList = stockInformationRepository.findTop10VolumeStocks()
        val stockLogList = StockLog.from(stockList)
        stockInformationRepository.saveTop10VolumeStocks(stockLogList)
        return CollectTopVolumeStocksResult(savedCount = stockLogList.size)
    }
}

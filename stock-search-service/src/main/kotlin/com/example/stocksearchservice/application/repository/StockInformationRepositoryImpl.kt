package com.example.stocksearchservice.application.repository

import com.example.stocksearchservice.application.port.out.StockInformationPort
import com.example.stocksearchservice.domain.Stock
import com.example.stocksearchservice.domain.repository.StockInformationRepository

class StockInformationRepositoryImpl(
        val stockInformationPort: StockInformationPort,
) : StockInformationRepository {
    override fun findTop10VolumeStocks(): List<Stock> {
        return stockInformationPort.getCurrentTop20StocksByTradingVolume().take(10).map { it.toStock() }
    }
}

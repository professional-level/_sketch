package com.example.application.repository

import com.example.application.port.out.StockInformationPort
import com.example.domain.Stock
import com.example.domain.repository.StockInformationRepository

class StockInformationRepositoryImpl(
    val stockInformationPort: StockInformationPort,
) : StockInformationRepository {
    override fun findTop10VolumeStocks(): List<Stock> {
        return stockInformationPort.getCurrentTop20StocksByTradingVolume().take(10).map { it.toStock() }
    }
}

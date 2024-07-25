package com.example.stocksearchservice.application.scheduler

import com.example.stocksearchservice.domain.repository.StockInformationRepository
import org.springframework.stereotype.Component

@Component
class StockSearchScheduler(
    private val stockInformationRepository: StockInformationRepository,
) {

    fun temp1() {
        stockInformationRepository.findTop10VolumeStocks()
    }
}

package com.example.stocksearchservice.application.repository

import com.example.stocksearchservice.application.port.out.StockStrategyPort
import com.example.stocksearchservice.application.port.out.dto.StockStrategyDTO
import com.example.stocksearchservice.application.port.out.dto.StrategyType
import com.example.stocksearchservice.domain.repository.StockStrategyRepository
import com.example.stocksearchservice.domain.strategy.FinalPriceBatingStrategyV1
import java.time.ZonedDateTime

class StockStrategyRepositoryImpl(
    private val stockStrategyPort: StockStrategyPort,
) : StockStrategyRepository {
    override fun save(entity: FinalPriceBatingStrategyV1, date: ZonedDateTime) {
        val stockId = entity.stock.stockId.value
        val type = StrategyType.FinalPriceBatingV1
        val dto = StockStrategyDTO(stockId = stockId, type = type, date = date)
        stockStrategyPort.save(dto)
    }
}
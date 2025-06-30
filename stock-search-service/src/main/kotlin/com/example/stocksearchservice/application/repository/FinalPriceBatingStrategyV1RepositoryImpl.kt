package com.example.stocksearchservice.application.repository

import com.example.common.AopEnabled
import com.example.common.domain.event.EventPublishingRepository
import com.example.stocksearchservice.application.port.out.StockStrategyPort
import com.example.stocksearchservice.application.port.out.dto.StockStrategyDTO
import com.example.stocksearchservice.application.port.out.dto.StrategyType
import com.example.stocksearchservice.domain.repository.FinalPriceBatingStrategyV1Repository
import com.example.stocksearchservice.domain.strategy.FinalPriceBatingStrategyV1
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@AopEnabled("com.example.stocksearchservice.application.event.CompleteEntityAspect")
@EventPublishingRepository
@Component
class FinalPriceBatingStrategyV1RepositoryImpl(
    private val stockStrategyPort: StockStrategyPort,
) : FinalPriceBatingStrategyV1Repository {
    override suspend fun save(entity: FinalPriceBatingStrategyV1, date: ZonedDateTime) {
        val stockId = entity.stock.stockId.value
        val type = StrategyType.FinalPriceBatingV1
        val dto = StockStrategyDTO(stockId = stockId, type = type, date = date)
        stockStrategyPort.save(dto)
    }

    override suspend fun saveAll(entities: List<FinalPriceBatingStrategyV1>, date: ZonedDateTime) {
        val dtos = entities.map { entity ->
            StockStrategyDTO(
                stockId = entity.stock.stockId.value,
                type = StrategyType.FinalPriceBatingV1,
                date = date
            )
        }
        stockStrategyPort.saveAll(dtos)
    }
}

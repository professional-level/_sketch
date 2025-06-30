package com.example.stocksearchservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stocksearchservice.adapter.out.persistence.entity.StockSuggestion
import com.example.stocksearchservice.adapter.out.persistence.repository.StockSuggestionRepository
import com.example.stocksearchservice.application.port.out.StockStrategyPort
import com.example.stocksearchservice.application.port.out.dto.StockStrategyDTO

@PersistenceAdapter
class StockStrategyAdapter(
    private val stockSuggestionRepository: StockSuggestionRepository,
) : StockStrategyPort {
    override suspend fun save(dto: StockStrategyDTO) {
        val entity = StockSuggestion.of(dto)
        stockSuggestionRepository.save(entity)
        // save가 실패하면?
    }
    
    override suspend fun saveAll(dtos: List<StockStrategyDTO>) {
        val entities = dtos.map { dto -> StockSuggestion.of(dto) }
        entities.forEach { entity ->
            stockSuggestionRepository.save(entity)
        }
        // TODO: 향후 진정한 배치 저장으로 개선 (bulk insert)
    }
}

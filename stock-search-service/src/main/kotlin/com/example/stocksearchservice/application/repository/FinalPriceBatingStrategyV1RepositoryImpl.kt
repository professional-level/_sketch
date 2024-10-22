package com.example.stocksearchservice.application.repository

import com.example.stocksearchservice.application.port.out.StockStrategyPort
import com.example.stocksearchservice.application.port.out.dto.StockStrategyDTO
import com.example.stocksearchservice.application.port.out.dto.StrategyType
import com.example.stocksearchservice.domain.event.EventPublishingRepository
import com.example.stocksearchservice.domain.repository.FinalPriceBatingStrategyV1Repository
import com.example.stocksearchservice.domain.strategy.FinalPriceBatingStrategyV1
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@EventPublishingRepository
@Component
class FinalPriceBatingStrategyV1RepositoryImpl(
    private val stockStrategyPort: StockStrategyPort,
    @Lazy private val proxy: FinalPriceBatingStrategyV1RepositoryImpl, // TODO: 개선 방안 모색 필요, 안티 패턴이라는 의견이 있었음
) : FinalPriceBatingStrategyV1Repository {
    override suspend fun save(entity: FinalPriceBatingStrategyV1, date: ZonedDateTime) {
        val stockId = entity.stock.stockId.value
        val type = StrategyType.FinalPriceBatingV1
        val dto = StockStrategyDTO(stockId = stockId, type = type, date = date)
        stockStrategyPort.save(dto)
    }

    // TODO: 같은 class(Component)의 save()를 호출할때 aop를 고려하도록 수정 필요
    override suspend fun saveAll(entities: List<FinalPriceBatingStrategyV1>, date: ZonedDateTime) {
        entities.forEach { proxy.save(it, date) }
    }
}

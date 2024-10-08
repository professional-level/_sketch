package com.example.stocksearchservice.config

import com.example.stocksearchservice.application.port.out.EventPublisherPort
import com.example.stocksearchservice.domain.repository.FinalPriceBatingStrategyV1Repository
import com.example.stocksearchservice.domain.strategy.FinalPriceBatingStrategyV1
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class StockStrategyService(
    private val finalPriceBatingStrategyV1Repository: FinalPriceBatingStrategyV1Repository,
    private val eventPublisher: EventPublisherPort,
) {
    suspend fun saveStrategies(strategies: List<FinalPriceBatingStrategyV1>, date: ZonedDateTime) {
        finalPriceBatingStrategyV1Repository.saveAll(strategies, date)
        // 이벤트 발행
        eventPublisher.publish("StrategiesSavedEvent") // 실제 이벤트 객체로 대체
    }
}

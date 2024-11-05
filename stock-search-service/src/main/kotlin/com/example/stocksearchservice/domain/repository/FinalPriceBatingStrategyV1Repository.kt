package com.example.stocksearchservice.domain.repository

import com.example.common.domain.event.SupportedEventEntityRepository
import com.example.stocksearchservice.domain.strategy.FinalPriceBatingStrategyV1
import com.example.stocksearchservice.domain.strategy.StockStrategy
import java.time.ZonedDateTime

interface FinalPriceBatingStrategyV1Repository : StockStrategyRepository<FinalPriceBatingStrategyV1>

// TODO: 공통 인터페이스 위치 이동 필요
interface StockStrategyRepository<T : StockStrategy> : SupportedEventEntityRepository<StockStrategy> {
    suspend fun save(entity: T, date: ZonedDateTime = ZonedDateTime.now())
    suspend fun saveAll(entities: List<T>, date: ZonedDateTime = ZonedDateTime.now())
}
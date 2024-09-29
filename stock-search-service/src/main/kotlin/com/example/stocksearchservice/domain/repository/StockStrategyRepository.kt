package com.example.stocksearchservice.domain.repository

import com.example.stocksearchservice.domain.strategy.FinalPriceBatingStrategyV1
import java.time.ZonedDateTime

interface StockStrategyRepository{
    fun save(entity: FinalPriceBatingStrategyV1, date: ZonedDateTime = ZonedDateTime.now())
}
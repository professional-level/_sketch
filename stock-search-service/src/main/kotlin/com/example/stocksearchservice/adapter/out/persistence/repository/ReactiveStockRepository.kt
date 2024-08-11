package com.example.stocksearchservice.adapter.out.persistence.repository

import com.example.stocksearchservice.adapter.out.persistence.entity.StockSuggestion
import com.example.stocksearchservice.adapter.out.persistence.entity.StockVolumeRank
import common.AbstractReactiveRepository
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository

@ApplicationScoped
@Repository
class StockSuggestionRepository : AbstractReactiveRepository<StockSuggestion, Long>()

@ApplicationScoped
@Repository
class StockVolumeRankRepository : AbstractReactiveRepository<StockVolumeRank, Long>()

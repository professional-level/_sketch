package com.example.stocksearchservice.adapter.out.persistence.repository

import com.example.stocksearchservice.adapter.out.persistence.entity.StockSuggestion
import common.AbstractReactiveRepository
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository


@ApplicationScoped
@Repository
class StockSuggestionRepository : AbstractReactiveRepository<StockSuggestion, Long>()
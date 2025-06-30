package com.example.stocksearchservice.domain.service

import com.example.stocksearchservice.domain.Stock
import com.example.stocksearchservice.domain.strategy.FinalPriceBatingStrategyV1
import org.springframework.stereotype.Component

@Component // 굳이 component일 필요가 있나 ?
class FinalPriceBatingStockAnalyzer {
    
    fun analyzeStocks(stocks: List<Stock>): List<FinalPriceBatingStrategyV1> {
        return stocks
            .filter { isValidStock(it) }
            .mapIndexed { index, stock -> 
                FinalPriceBatingStrategyV1.of(stock, index + 1)
            }
    }
    
    private fun isValidStock(stock: Stock): Boolean {
        return stock.stockDerivative.value >= 0 && 
               stock.stockVolume.value >= 30000
    }
}
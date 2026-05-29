package com.example.stocksearchservice.application.config

import com.example.stocksearchservice.domain.service.FinalPriceBatingStockAnalyzer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DomainServiceConfiguration {
    @Bean
    fun finalPriceBatingStockAnalyzer(): FinalPriceBatingStockAnalyzer {
        return FinalPriceBatingStockAnalyzer()
    }
}

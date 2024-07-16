package com.example.stocksearchservice.domain.repository

import com.example.stocksearchservice.domain.Stock

interface StockInformationRepository {
    fun findTop10VolumeStocks(): List<Stock>
}

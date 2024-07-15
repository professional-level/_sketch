package com.example.domain.repository

import com.example.domain.Stock

interface StockInformationRepository {
    fun findTop10VolumeStocks(): List<Stock>
}

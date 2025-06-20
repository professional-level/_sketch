package com.example.stock.domain.repository

import com.example.stock.domain.Stock
import com.example.stock.domain.StockId
import com.example.common.DomainRepository

interface StockRepository : DomainRepository<Stock, StockId> {
}
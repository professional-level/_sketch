package com.example.stock.stock.domain.repository

import com.example.stock.common.DomainRepository
import com.example.stock.domain.Stock
import com.example.stock.domain.StockId

interface StockRepository : DomainRepository<Stock, StockId> {
}
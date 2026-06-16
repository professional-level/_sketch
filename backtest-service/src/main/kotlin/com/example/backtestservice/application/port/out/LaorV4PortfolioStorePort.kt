package com.example.backtestservice.application.port.out

import com.example.backtestservice.domain.backtest.LaorV4PortfolioRecord
import java.util.UUID

interface LaorV4PortfolioStorePort {
    fun save(record: LaorV4PortfolioRecord)
    fun findById(portfolioId: UUID): LaorV4PortfolioRecord?
    fun findAll(): List<LaorV4PortfolioRecord>
}

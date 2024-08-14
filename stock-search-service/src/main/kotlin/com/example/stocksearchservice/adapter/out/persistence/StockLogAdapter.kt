package com.example.stocksearchservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stocksearchservice.adapter.out.persistence.entity.StockVolumeRank
import com.example.stocksearchservice.adapter.out.persistence.repository.StockVolumeRankRepository
import com.example.stocksearchservice.application.port.out.StockLogPort
import com.example.stocksearchservice.application.port.out.dto.StockVolumeRankDTO

@PersistenceAdapter
class StockLogAdapter(
    private val stockVolumeRankRepository: StockVolumeRankRepository
) : StockLogPort {
    override suspend fun saveStockVolumeRankInfo(items: List<StockVolumeRankDTO>) {
        items.map {  stockVolumeRankRepository.save(StockVolumeRank.from(it))}
    }
}
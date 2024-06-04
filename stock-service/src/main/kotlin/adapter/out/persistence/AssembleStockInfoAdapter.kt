package com.example.stock.adapter.out.persistence

import com.example.stock.adapter.out.persistence.repository.StockJpaRepository
import com.example.stock.application.port.out.AssembleStockInfoPort
import com.example.stock.application.port.out.dto.StockDTO
import com.example.stock.common.PersistenceAdapter

@PersistenceAdapter
class AssembleStockInfoAdapter(
    private val repository: StockJpaRepository,
) : AssembleStockInfoPort {
    override fun assembleStockInfo(
        stockId: Int,
        stockName: String,
        stockDerivative: Double,
        stockPrice: Int,
    ): StockDTO {
        val jpaEntity = repository.save() // TODO: 예제라서 save 구현이 없음
        return jpaEntity.toDTO()
    }
}
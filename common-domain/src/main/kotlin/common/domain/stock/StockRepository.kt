package common.domain.stock

import common.DomainRepository

/**
 * 통합된 Stock Repository 인터페이스
 * 모든 서비스에서 공통으로 사용할 수 있는 Stock 레포지토리
 */
interface StockRepository : DomainRepository<Stock, StockId> {
    
    /**
     * Stock ID로 Stock 조회
     */
    suspend fun findByStockId(stockId: StockId): Stock?
    
    /**
     * Stock 이름으로 검색
     */
    suspend fun findByStockName(stockName: StockName): List<Stock>
    
    /**
     * Stock 저장
     */
    suspend fun save(stock: Stock): Stock
    
    /**
     * 여러 Stock 저장
     */
    suspend fun saveAll(stocks: List<Stock>): List<Stock>
    
    /**
     * 모든 Stock 조회
     */
    suspend fun findAll(): List<Stock>
    
    /**
     * Stock 존재 여부 확인
     */
    suspend fun existsByStockId(stockId: StockId): Boolean
}
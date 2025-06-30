package com.example.stocksearchservice.domain.repository

import com.example.stocksearchservice.domain.ForeignerStockVolume
import com.example.stocksearchservice.domain.InstitutionStockVolume
import com.example.stocksearchservice.domain.Stock
import com.example.stocksearchservice.domain.StockDerivative
import com.example.stocksearchservice.domain.StockId
import com.example.stocksearchservice.domain.StockLog
import com.example.stocksearchservice.domain.StockPrice
import com.example.stocksearchservice.domain.StockVolume

interface StockInformationRepository {
    fun findTop10VolumeStocks(): List<Stock> // 당일, 현재 거래대금 Top 10
    fun isVolumeIsHighestIn5Days(id: StockId): Boolean // 5일간 해당종목의 거래대금이 최상위
    fun getHighestPriceAtCurrentDay(id: StockId): StockPrice // 당일 최고 가격
    fun getProgramPureBuyingVolumeAtLatestOfDay(id: StockId): StockVolume? // 당일 프로그램 순매수량
    fun isHighestProgramVolumeIn5Days(id: StockId): Boolean // 5거래일간 최대 프로그램 순매수량이 최상위
    fun getPriceDifferenceDerivativeBetweenHighestAndEnd(id: StockId): StockDerivative
    fun getInstitutionAndForeignerFlowsOfDay(id: StockId): Pair<InstitutionStockVolume, ForeignerStockVolume>
    // save
    // TODO: StockVolume을 두개로 기관, 외국인으로 나눠야한다
    suspend fun saveTop10VolumeStocks(stockLogs: List<StockLog>)
} // TODO: DomainRepository를 상속하는 형태로 구축 필요

package com.example.stocksearchservice.domain.repository

import com.example.stocksearchservice.domain.Stock
import com.example.stocksearchservice.domain.StockDerivative
import com.example.stocksearchservice.domain.StockId
import com.example.stocksearchservice.domain.StockPrice
import com.example.stocksearchservice.domain.StockVolume

interface StockInformationRepository {
    fun findTop10VolumeStocks(): List<Stock> // 당일, 현재 거래대금 Top 10
    fun isVolumeIsHighestIn5Days(id: StockId): Boolean // 5일간 해당종목의 거래대금이 최상위
    fun getHighestPriceAtCurrentDay(id: StockId): StockPrice // 당일 최고 가격
    fun getProgramPureBuyingVolumeAtEndOfDay(id: StockId): StockVolume // 당일 프로그램 순매수량
    fun getPriceDifferenceDerivativeBetweenHighestAndEnd(id: StockId): StockDerivative
    fun getInstitutionAndForeignerFlowsOfDay(id: StockId): Pair<StockVolume, StockVolume> // TODO: StockVolume을 두개로 기관, 외국인으로 나눠야한다
}
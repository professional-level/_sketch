package com.example.stocksearchservice.application.port.out

import com.example.stocksearchservice.application.port.out.dto.SimpleStockDTO
import com.example.stocksearchservice.application.port.out.dto.StockProgramVolume

interface StockInformationPort {
    // 당일, 현재 거래대금 상위 20종목을 가져옵니다.
    fun getCurrentTop20StocksByTradingVolume(): List<SimpleStockDTO>

    // 해당 종목의 오늘 거래대금이 최근 5영업일 동안 최상위를 찾아오기 위한 메서드
    fun getStockVolumeDuring7days(id: String): List<SimpleStockDTO> // 일단 5거래일이상의 거래대금을 가져온다.

    // 해당 종목의 당일 최고 상승률을 가져온다.
    fun getHighestPriceAtCurrentDay(id: String): SimpleStockDTO // TODO: 반환용 DTO에 대한 고민이 필요

    // 지금 시점의 프로그램 순매수금액
    fun getCurrentProgramPureBuyingVolume(id: String, date: String): Long? // TODO: 이걸 보니 확실히 DTO가 많아야 하는것이 생각이 듬

    // 지금 시점의 종가 및 등락률
    fun getCurrentPrice(id: String): SimpleStockDTO

    // 외국인+기관 수급
    fun getInstitutionAndForeignerFlowsOfDay(id: String, date: String): SimpleStockDTO

    fun getInstitutionFlowsOfDay(id: String, date: String): SimpleStockDTO

    fun getForeignerFlowsOfDay(id: String, date: String): SimpleStockDTO

    // ~거래일동안의 프로그램 거래대금
    fun getProgramVolumeByStockIdIn(id: String, days: Int): List<StockProgramVolume>

    /** TODO: 추가적으로 개발 필요
     // 당일 개인 수급이 -
     // 5일간 외국인+기간 수급이 최대치
     */
}

package com.example.stocksearchservice.application.port.out

import com.example.stocksearchservice.application.port.out.dto.SimpleStockDTO

interface StockInformationPort {
    fun getCurrentTop20StocksByTradingVolume(): List<SimpleStockDTO> // 현재 시점의 거래대금 상위 20종목을 가져옵니다.
}

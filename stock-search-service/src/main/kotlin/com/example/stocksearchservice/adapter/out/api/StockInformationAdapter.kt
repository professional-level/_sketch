package com.example.stocksearchservice.adapter.out.api

import com.example.common.ExternalApiAdapter
import com.example.stocksearchservice.application.port.out.StockInformationPort
import com.example.stocksearchservice.application.port.out.dto.SimpleStockDTO

@ExternalApiAdapter
class StockInformationAdapter : StockInformationPort {
    /** TODO: stock의 information을 가져오는 service는 다양할 수 있어서,
     * 정확히 증권사 open-api를 사용해서 데이터를 가져오는 서비스임을 알리는 네이밍이 필요하다. */
    override fun getCurrentTop20StocksByTradingVolume(): List<SimpleStockDTO> {
        return emptyList() // TODO: 실제 구현 필요
    }
}

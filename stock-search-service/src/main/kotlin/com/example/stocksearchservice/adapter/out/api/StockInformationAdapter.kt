package com.example.stocksearchservice.adapter.out.api

import com.example.common.ExternalApiAdapter
import com.example.stocksearchservice.adapter.out.api.handler.GetCurrentProgramPureBuyingVolumeHandler
import com.example.stocksearchservice.adapter.out.api.handler.GetCurrentProgramPureBuyingVolumeQuery
import com.example.stocksearchservice.adapter.out.api.handler.GetCurrentTop20StocksByTradingVolumeHandler
import com.example.stocksearchservice.adapter.out.api.handler.GetProgramVolumeInHandler
import com.example.stocksearchservice.adapter.out.api.handler.GetProgramVolumeInQuery
import com.example.stocksearchservice.application.port.out.StockInformationPort
import com.example.stocksearchservice.application.port.out.dto.SimpleStockDTO
import com.example.stocksearchservice.application.port.out.dto.StockProgramVolume
import common.UnitQuery

@ExternalApiAdapter
internal class StockInformationAdapter(
    private val getCurrentTop20StocksByTradingVolumeHandler: GetCurrentTop20StocksByTradingVolumeHandler,
    private val getProgramVolumeInHandler: GetProgramVolumeInHandler,
    private val getCurrentProgramPureBuyingVolumeHandler: GetCurrentProgramPureBuyingVolumeHandler,
) : StockInformationPort {
    /** TODO: stock의 information을 가져오는 service는 다양할 수 있어서,
     * 정확히 증권사 open-api를 사용해서 데이터를 가져오는 서비스임을 알리는 네이밍이 필요하다. */
    override fun getCurrentTop20StocksByTradingVolume(): List<SimpleStockDTO> {
        return getCurrentTop20StocksByTradingVolumeHandler.execute(UnitQuery())
    }

    override fun getProgramVolumeByStockIdIn(id: String, days: Int): List<StockProgramVolume> {
        return getProgramVolumeInHandler.execute(GetProgramVolumeInQuery(id)).take(days)
    }

    override fun getStockVolumeDuring7days(id: String): List<SimpleStockDTO> {
        TODO("Not yet implemented")
    }

    override fun getHighestPriceAtCurrentDay(id: String): SimpleStockDTO {
        TODO("Not yet implemented")
    }

    override fun getCurrentProgramPureBuyingVolume(id: String, date: String): Long {
        return getCurrentProgramPureBuyingVolumeHandler.execute(GetCurrentProgramPureBuyingVolumeQuery(id, date))
    }

    override fun getCurrentPrice(id: String): SimpleStockDTO {
        TODO("Not yet implemented")
    }

    override fun getInstitutionAndForeignerFlowsOfDay(id: String, date: String): SimpleStockDTO {
        TODO("Not yet implemented")
    }

    override fun getInstitutionFlowsOfDay(id: String, date: String): SimpleStockDTO {
        TODO("Not yet implemented")
    }

    override fun getForeignerFlowsOfDay(id: String, date: String): SimpleStockDTO {
        TODO("Not yet implemented")
    }
}

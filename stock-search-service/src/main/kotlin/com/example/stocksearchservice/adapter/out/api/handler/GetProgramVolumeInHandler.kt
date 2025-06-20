package com.example.stocksearchservice.adapter.out.api.handler

import ProgramTradeVolume
import com.example.stocksearchservice.application.port.out.dto.StockProgramVolume
import com.example.common.Query
import com.example.common.StringExtension
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
internal class GetProgramVolumeInHandler(
    override val stockApiClient: WebClient,
): ApiQueryHandler<GetProgramVolumeInQuery,List<StockProgramVolume>>(){
    override fun execute(context: GetProgramVolumeInQuery): List<StockProgramVolume> {
        val date = StringExtension.defaultCurrentTime()
        val response = stockApiClient
            .get()
            .uri("/open-api/program/individual/${context.id}?date=$date") // TODO: 이 date가 사실은 handler 이전에 넘어와야함
            .accept(MediaType.APPLICATION_PROTOBUF)
            .retrieve()
            .toEntity(ProgramTradeVolume.ProgramStockList::class.java)
            .block()
        return response?.body?.itemsList?.map {
            val stockPrice = it.stckClpr.toInt()
            StockProgramVolume(
                stockId = context.id,
                stockPrice = stockPrice,
                stockDerivative = it.prdyCtrt.toDouble(), // TODO: raw data는 String으로 받는것을 고려 중
                stockVolume = it.acmlVol.toLong(),
                programVolume = it.wholSmtnNtbyTrPbmn.toLong(),
                date = it.stckBsopDate,
            )
        } ?: throw RuntimeException("Error getting stock data") // TODO: exception 처리 제대로 필요
    }
}
data class GetProgramVolumeInQuery(val id: String): Query<GetProgramVolumeInQuery>
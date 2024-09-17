package com.example.stocksearchservice.adapter.out

import ProgramTradeVolume
import com.example.stocksearchservice.application.port.out.dto.StockProgramVolume
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
internal class GetProgramVolumeInHandler(
    private val stockApiClient: WebClient,
) {
    fun execute(id: String): List<StockProgramVolume> {
        val response = stockApiClient
            .get()
            .uri("/open-api/program/individual/$id")
            .accept(MediaType.APPLICATION_PROTOBUF)
            .retrieve()
            .toEntity(ProgramTradeVolume.ProgramStockList::class.java)
            .block()
        return response?.body?.itemsList?.map {
            val stockPrice = it.stckClpr.toInt()
            StockProgramVolume(
                stockId = id,
                stockPrice = stockPrice,
                stockDerivative = it.prdyCtrt.toDouble(), // TODO: raw data는 String으로 받는것을 고려 중
                stockVolume = it.acmlVol.toLong(),
                programVolume = it.wholSmtnNtbyTrPbmn.toLong(),
                date = it.stckBsopDate,
            )
        } ?: throw RuntimeException("Error getting stock data") // TODO: exception 처리 제대로 필요
    }
}

package com.example.stocksearchservice.adapter.out

import ProgramTradeVolume
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
internal class GetCurrentProgramPureBuyingVolumeHandler(
    private val stockApiClient: WebClient,
) {
    // TODO: handler에 대한 법칙 정의 필요
    fun execute(id: String, date: String): Long {
        val response = stockApiClient
            .get()
            .uri("/open-api/program/individual/$id/detail")
            .accept(MediaType.APPLICATION_PROTOBUF)
            .retrieve()
            .toEntity(ProgramTradeVolume.ProgramStockOfDateTime::class.java)
            .block()
        val volume = response.body.wholSmtnNtbyTrPbmn
        return volume.toLong()
    }
}
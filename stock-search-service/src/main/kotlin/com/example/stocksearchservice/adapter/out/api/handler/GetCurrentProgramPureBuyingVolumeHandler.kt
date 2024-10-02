package com.example.stocksearchservice.adapter.out.api.handler

import ProgramTradeVolume
import common.Query
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
internal class GetCurrentProgramPureBuyingVolumeHandler(
    override val stockApiClient: WebClient,
) : ApiQueryHandler<GetCurrentProgramPureBuyingVolumeQuery, Long>() {
    // TODO: handler에 대한 법칙 정의 필요
    override fun execute(context: GetCurrentProgramPureBuyingVolumeQuery): Long {
        val response = stockApiClient
            .get()
            .uri("/open-api/program/individual/${context.id}/detail")
            .accept(MediaType.APPLICATION_PROTOBUF)
            .retrieve()
            .toEntity(ProgramTradeVolume.ProgramStockOfDateTime::class.java)
            .block()
        val volume = response?.body?.wholSmtnNtbyTrPbmn ?: throw RuntimeException("Error getting stock data")
        return volume.toLong()
    }
}

internal data class GetCurrentProgramPureBuyingVolumeQuery(
    val id: String,
    val date: String,
) : Query<GetCurrentProgramPureBuyingVolumeQuery>
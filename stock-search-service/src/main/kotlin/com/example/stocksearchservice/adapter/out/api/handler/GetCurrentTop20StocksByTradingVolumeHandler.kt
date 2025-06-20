package com.example.stocksearchservice.adapter.out.api.handler

import VolumeRank
import com.example.stocksearchservice.application.port.out.dto.SimpleStockDTO
import com.example.common.UnitQuery
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime

@Component
internal class GetCurrentTop20StocksByTradingVolumeHandler(
    override val stockApiClient: WebClient,
) : ApiQueryHandler<UnitQuery, List<SimpleStockDTO>>() {
    override fun execute(context: UnitQuery): List<SimpleStockDTO> {
        val response = stockApiClient
            .get()
            .uri("/open-api/quotations/volume-rank")
            .accept(MediaType.APPLICATION_PROTOBUF)
            .retrieve()
            .toEntity(VolumeRank.StockMap::class.java)
            .block() // TODO: webclient의 block()은 스레드 block을 유도하는지 확인 필요

        return response?.body?.itemsMap?.map {
            val stockPrice = it.value.stckPrpr.toInt()
            SimpleStockDTO(
                stockId = it.key,
                stockName = it.value.htsKorIsnm,
                stockPrice = stockPrice,
                stockDerivative = it.value.prdyCtrt.toDouble(), // TODO: raw data는 String으로 받는것을 고려 중
                stockVolume = it.value.acmlTrPbmn.toLong(),
                stockTotalVolume = (it.value.lstnStcn).toLong() * stockPrice, // TODO: total volume 데이터 확인
                date = LocalDateTime.now().toString(), // TODO: date 맵핑을 어떻게 할지 고민 필요
            )
        } ?: throw RuntimeException("Error getting stock data") // TODO: exception 처리 제대로 필요
    }
}

package com.example.stocksearchservice.adapter.out

import VolumeRank
import com.example.stocksearchservice.application.port.out.dto.SimpleStockDTO
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime

// TODO: 좀 더 세분화 할 방법이 없을까?
interface Handler<T> {
    fun execute()
}

@Component
internal class GetCurrentTop20StocksByTradingVolumeHandler(
    private val stockApiClient: WebClient,
) {
    fun execute(): List<SimpleStockDTO> {
        val response = stockApiClient
            .get()
            .uri("/open-api/quotations/volume-rank")
            .accept(MediaType.APPLICATION_PROTOBUF)
            .retrieve()
            .toEntity(VolumeRank.StockMap::class.java)
            .block()

        return response?.body?.itemsMap?.map {
            val stockPrice = it.value.stckPrpr.toInt()
            SimpleStockDTO(
                stockId = it.key.toInt(),
                stockName = it.value.htsKorIsnm,
                stockPrice = stockPrice,
                stockDerivative = it.value.prdyCtrt.toDouble(), // TODO: raw data는 String으로 받는것을 고려 중
                stockVolume = it.value.acmlVol.toLong(),
                stockTotalVolume = (it.value.lstnStcn).toLong() * stockPrice, // TODO: total volume 데이터 확인
                date = LocalDateTime.now().toString(), // TODO: date 맵핑을 어떻게 할지 고민 필요
            )
        } ?: throw RuntimeException("Error getting stock data") // TODO: exception 처리 제대로 필요
    }
}

@Configuration
class WebClientConfig {
    @Bean
    fun stockApiClient(): WebClient {
        val port = 8079
        // TODO: uri 설정을 추후 제대로 해야 함
        return WebClient.create("http://localhost:$port")
    }
}

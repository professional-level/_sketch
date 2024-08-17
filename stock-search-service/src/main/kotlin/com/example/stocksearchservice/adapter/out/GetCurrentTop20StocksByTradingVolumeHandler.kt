package com.example.stocksearchservice.adapter.out

import com.example.stocksearchservice.application.port.out.dto.SimpleStockDTO
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

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
            .uri("/quotations/volume-rank")
            .accept(MediaType.APPLICATION_PROTOBUF)
            .retrieve()
            .toEntity(JsonNode::class.java)
            .block()
        response.body.map {
//            SimpleStockDTO()
        }
        return emptyList()
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
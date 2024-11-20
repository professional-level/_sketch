package com.example.stockpurchaseservice.adapter.out

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@ExternalApiAdapter // TODO: 어노테이션 체크
internal class MarketServiceAdapter(
    private val buyStockHandler: BuyStockHandler,
) : MarketServicePort {
    override fun buyStock(order: PurchaseOrderDto) {
        buyStockHandler.execute()
    }
}

@Component
internal class BuyStockHandler(
    private val stockApiClient: WebClient,
) {
    fun execute() {
        val uri = "temp_uri"
        val response = stockApiClient
            .get()
            .uri(uri)
            .accept(MediaType.APPLICATION_PROTOBUF)
            .retrieve()
            .toEntity(ResponseEntity::class.java) // TODO: map to ProtoType Entity
            .block()
        // TODO: 유효성 체크
        response?.body?.body
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

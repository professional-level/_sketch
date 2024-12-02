package com.example.stockpurchaseservice.adapter.out

import ApiResponse
import com.example.common.ExternalApiAdapter
import com.example.common.endpoint.Endpoint.POST_STOCK_ORDER
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import kotlin.math.round

@ExternalApiAdapter // TODO: 어노테이션 체크
internal class MarketServiceAdapter(
    private val buyStockHandler: BuyStockHandler,
    private val sellStockHandler: SellStockHandler,
) : MarketServicePort {
    override fun buyStock(order: PurchaseOrderDto) {
        buyStockHandler.execute(order)
    }

    override fun sellStock(order: SellingOrderDto) {
        sellStockHandler.execute(order)
    }
}

@Component
internal class SellStockHandler(
    private val stockApiClient: WebClient,
) {
    fun execute(order: SellingOrderDto) {

    }
}

@Component
internal class BuyStockHandler(
    private val stockApiClient: WebClient,
) {
    fun execute(order: PurchaseOrderDto) {
        val uri = POST_STOCK_ORDER
        val quantity = order.quantity

        val body = mapOf(
            "PDNO" to order.stockId,
            "ORD_DVSN" to "00",
            "ORD_QTY" to quantity,
            "ORD_UNPR" to order.purchasePrice,
        )
        val response = stockApiClient
            .post()
            .uri(uri)
            .accept(MediaType.APPLICATION_PROTOBUF)
            .bodyValue(body)
            .retrieve()
            .toEntity(ApiResponse.StockOrder::class.java)
            .block()
        // 유효성 체크
        if (response?.body?.rtCd != "0") {
            throw RuntimeException("request failed") // TODO: exception mapping 필요
        }
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

data class StockOrderRequest(
    val PDNO: String, // 종목코드 (6자리) 삼성전자:005930
    val ORD_DVSN: String = "00", // 주문구분 (2자리 코드)
    val ORD_QTY: Long, // 주문수량 (String)
    val ORD_UNPR: Long, // 주문단가 (String)
    val isMock: Boolean = true,
)

// TODO: properties 혹은 다른 옵션으로 관리 필요
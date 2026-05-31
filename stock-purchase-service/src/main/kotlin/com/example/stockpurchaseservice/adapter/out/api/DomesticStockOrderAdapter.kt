package com.example.stockpurchaseservice.adapter.out.api

import ApiResponse
import com.example.common.ExternalApiAdapter
import com.example.common.endpoint.Endpoint.POST_STOCK_ORDER
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.DomesticStockOrderPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@ExternalApiAdapter
internal class DomesticStockOrderAdapter(
    @Qualifier("stockApiClient") private val stockApiClient: WebClient,
) : DomesticStockOrderPort {

    override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
        val body = mapOf(
            "PDNO" to order.stockId,
            "ORD_DVSN" to order.orderType.toDomesticOrderDivision(),
            "ORD_QTY" to order.quantity,
            "ORD_UNPR" to order.purchasePrice.toLong(),
        )

        return stockApiClient.submitStockOrder(
            uri = OPEN_API_PREFIX + POST_STOCK_ORDER,
            body = body,
            fallbackOrderId = order.orderId.toString(),
        )
    }

    override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
        return BrokerOrderSubmissionDto(externalOrderId = order.orderId.toString())
    }

    private fun StockOrderType.toDomesticOrderDivision(): String {
        return when (this) {
            StockOrderType.LIMIT,
            StockOrderType.LOC -> "00"

            StockOrderType.MOC -> "01"
        }
    }
}

internal const val OPEN_API_PREFIX = "/open-api"

internal fun WebClient.submitStockOrder(
    uri: String,
    body: Map<String, Any>,
    fallbackOrderId: String,
): BrokerOrderSubmissionDto {
    val response = post()
        .uri(uri)
        .accept(MediaType.APPLICATION_PROTOBUF)
        .bodyValue(body)
        .retrieve()
        .toEntity(ApiResponse.StockOrder::class.java)
        .awaitExternalApi()
        ?: throw RuntimeException("stock order response is empty")

    val order = response.body ?: throw RuntimeException("stock order body is empty")
    if (order.rtCd != "0") {
        throw RuntimeException("stock order request failed: ${order.msgCd} ${order.msg1}".trim())
    }

    val externalOrderId = order.output.getODNO().takeIf { it.isNotBlank() } ?: fallbackOrderId
    return BrokerOrderSubmissionDto(externalOrderId = externalOrderId)
}

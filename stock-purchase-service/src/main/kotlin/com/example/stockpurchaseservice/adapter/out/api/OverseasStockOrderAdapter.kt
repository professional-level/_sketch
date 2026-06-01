package com.example.stockpurchaseservice.adapter.out.api

import com.example.common.ExternalApiAdapter
import com.example.common.endpoint.Endpoint.POST_OVERSEAS_STOCK_ORDER
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OverseasStockOrderPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

@ExternalApiAdapter
internal class OverseasStockOrderAdapter(
    @Qualifier("stockApiClient") private val stockApiClient: WebClient,
    @Value("\${akra.order.overseas.mock:true}") private val isMockOrder: Boolean,
) : OverseasStockOrderPort {

    override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
        val body = order.toUsOverseasBuyRequest(isMockOrder)
        return stockApiClient.submitStockOrder(
            uri = OPEN_API_PREFIX + POST_OVERSEAS_STOCK_ORDER,
            body = body,
            fallbackOrderId = order.orderId.toString(),
        )
    }

    override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
        val body = order.toUsOverseasSellRequest(isMockOrder)
        return stockApiClient.submitStockOrder(
            uri = OPEN_API_PREFIX + POST_OVERSEAS_STOCK_ORDER,
            body = body,
            fallbackOrderId = order.orderId.toString(),
        )
    }
}

internal fun PurchaseOrderDto.toUsOverseasBuyRequest(isMock: Boolean): Map<String, Any> {
    return mapOf(
        "PDNO" to stockId.uppercase(),
        "OVRS_EXCG_CD" to exchangeForUsStock(stockId),
        "ORD_QTY" to quantity,
        "OVRS_ORD_UNPR" to purchasePrice.toBrokerPrice(),
        "ORD_DVSN" to orderType.toUsBuyOrderDivision(isMock),
        "CTAC_TLNO" to "",
        "MGCO_APTM_ODNO" to "",
        "ORD_SVR_DVSN_CD" to "0",
        "isMock" to isMock,
    )
}

private fun exchangeForUsStock(symbol: String): String {
    return when (symbol.uppercase()) {
        "TQQQ", "SOXL" -> "NASD"
        else -> "NASD"
    }
}

private fun StockOrderType.toUsBuyOrderDivision(isMock: Boolean): String {
    if (isMock) return "00"

    return when (this) {
        StockOrderType.LIMIT -> "00"
        StockOrderType.LOC -> "34"
        StockOrderType.MOC -> throw UnsupportedOperationException("US overseas buy does not support MOC")
    }
}

internal fun SellingOrderDto.toUsOverseasSellRequest(isMock: Boolean): Map<String, Any> {
    return mapOf(
        "PDNO" to stockId.uppercase(),
        "OVRS_EXCG_CD" to exchangeForUsStock(stockId),
        "ORD_QTY" to quantity,
        "OVRS_ORD_UNPR" to sellingPrice.toBrokerPrice(),
        "ORD_DVSN" to orderType.toUsSellOrderDivision(isMock),
        "SLL_TYPE" to "00",
        "CTAC_TLNO" to "",
        "MGCO_APTM_ODNO" to "",
        "ORD_SVR_DVSN_CD" to "0",
        "isMock" to isMock,
    )
}

private fun StockOrderType.toUsSellOrderDivision(isMock: Boolean): String {
    if (isMock) return "00"

    return when (this) {
        StockOrderType.LIMIT -> "00"
        StockOrderType.LOC -> "34"
        StockOrderType.MOC -> "33"
    }
}

private fun Double.toBrokerPrice(): String {
    return BigDecimal.valueOf(this)
        .stripTrailingZeros()
        .toPlainString()
}

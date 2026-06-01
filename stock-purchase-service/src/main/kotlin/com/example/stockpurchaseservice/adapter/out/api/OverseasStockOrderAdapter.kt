package com.example.stockpurchaseservice.adapter.out.api

import com.example.common.ExternalApiAdapter
import com.example.common.endpoint.Endpoint.GET_OVERSEAS_EXECUTION_ORDERS
import com.example.common.endpoint.Endpoint.POST_OVERSEAS_STOCK_ORDER
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.OverseasStockOrderPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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

    override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
        return fetchOrderHistoryRows().mapNotNull { it.toExecutionDto() }
    }

    override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
        return fetchOrderHistoryRows(
            symbol = query.symbol,
            date = query.submittedAt ?: ZonedDateTime.now(KIS_OVERSEAS_ZONE),
        ).findStatusFor(query)
    }

    private fun fetchOrderHistoryRows(
        symbol: String = "%",
        date: ZonedDateTime = ZonedDateTime.now(KIS_OVERSEAS_ZONE),
    ): List<KisBrokerOrderHistoryRow> {
        val response = stockApiClient.getExternalApi(
            uri = OPEN_API_PREFIX + GET_OVERSEAS_EXECUTION_ORDERS,
            queryParameters = overseasExecutionOrderQuery(symbol, date),
            responseType = JsonNode::class.java,
        )
        val returnCode = response.path("rt_cd").asText("")
        if (returnCode.isNotBlank() && returnCode != "0") {
            throw RuntimeException(
                "overseas execution lookup failed: ${response.path("msg_cd").asText()} ${response.path("msg1").asText()}".trim(),
            )
        }
        val output = response.path("output")
        val rows = when {
            output.isArray -> output.toList()
            output.isObject -> listOf(output)
            else -> emptyList()
        }
        return rows.mapNotNull { it.toHistoryRow() }
    }

    private fun overseasExecutionOrderQuery(
        symbol: String,
        date: ZonedDateTime,
    ): Map<String, String> {
        val yyyymmdd = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        return mapOf(
            "isMock" to isMockOrder.toString(),
            "pdno" to if (isMockOrder) "" else symbol.ifBlank { "%" }.uppercase(),
            "ordStrtDt" to yyyymmdd,
            "ordEndDt" to yyyymmdd,
            "sllBuyDvsn" to "00",
            "ccldNccsDvsn" to "00",
            "ovrsExcgCd" to if (isMockOrder) "" else "NASD",
            "sortSqn" to "DS",
            "ordDt" to "",
            "ordGnoBrno" to "",
            "odno" to "",
            "ctxAreaNk200" to "",
            "ctxAreaFk200" to "",
        )
    }

    private fun JsonNode.toHistoryRow(): KisBrokerOrderHistoryRow? {
        val orderId = path("odno").asText("").trim()
        if (orderId.isBlank()) return null
        val orderedQuantity = path("ft_ord_qty").asText("").toLongValue()
        val filledQuantity = path("ft_ccld_qty").asText("").toLongValue()
        val remainingQuantity = path("nccs_qty").asText("").toLongValue()
        val statusName = path("prcs_stat_name").asText("").takeIf { it.isNotBlank() }
        val rejectionReason = path("rjct_rson").asText("").takeIf { it.isNotBlank() }
        return KisBrokerOrderHistoryRow(
            externalOrderId = orderId,
            branchOrderNumber = path("ord_gno_brno").asText("").takeIf { it.isNotBlank() },
            symbol = path("pdno").asText("").trim(),
            stockName = path("prdt_name").asText("").trim(),
            orderedAt = parseKisOrderDateTime(
                path("ord_dt").asText("").ifBlank { path("dmst_ord_dt").asText("") },
                path("ord_tmd").asText("").ifBlank { path("thco_ord_tmd").asText("") },
            ),
            orderedQuantity = orderedQuantity,
            cumulativeFilledQuantity = filledQuantity,
            remainingQuantity = remainingQuantity,
            rejectedQuantity = if (rejectionReason != null) orderedQuantity else 0,
            cancelledQuantity = if (statusName?.contains(CANCELLED_KOREAN) == true) remainingQuantity else 0,
            cancelled = statusName?.contains(CANCELLED_KOREAN) == true,
            side = path("sll_buy_dvsn_cd").asText("").toOrderIntentSide(),
            averageExecutionPrice = path("ft_ccld_unpr3").asText("").toDoubleValue(),
            statusMessage = statusName,
            rejectionReason = rejectionReason,
        )
    }

    companion object {
        private val KIS_OVERSEAS_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
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

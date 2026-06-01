package com.example.stockpurchaseservice.adapter.out.broker

import ApiResponse
import DailyExecutionOrdersResponseOuterClass
import com.example.common.ExternalApiAdapter
import com.example.common.endpoint.Endpoint.GET_EXECUTION_ORDERS
import com.example.common.endpoint.Endpoint.GET_OVERSEAS_EXECUTION_ORDERS
import com.example.common.endpoint.Endpoint.POST_OVERSEAS_STOCK_ORDER
import com.example.common.endpoint.Endpoint.POST_STOCK_ORDER
import com.example.stockpurchaseservice.adapter.out.api.awaitExternalApi
import com.example.stockpurchaseservice.adapter.out.api.getExternalApi
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@ExternalApiAdapter
internal class KisBrokerGatewayAdapter(
    @Qualifier("stockApiClient") private val stockApiClient: WebClient,
) : BrokerGateway {

    override fun submitOrder(command: BrokerOrderCommand): BrokerOrderSubmissionDto {
        return when (command.market) {
            StockOrderMarket.DOMESTIC -> stockApiClient.submitStockOrder(
                uri = OPEN_API_PREFIX + POST_STOCK_ORDER,
                body = command.toKisDomesticOrderRequest(),
                fallbackOrderId = command.internalOrderId.toString(),
            )

            StockOrderMarket.OVERSEAS_US -> stockApiClient.submitStockOrder(
                uri = OPEN_API_PREFIX + POST_OVERSEAS_STOCK_ORDER,
                body = command.toKisUsOverseasOrderRequest(),
                fallbackOrderId = command.internalOrderId.toString(),
            )
        }
    }

    override fun findOrderHistory(query: BrokerOrderHistoryQuery): List<BrokerOrderHistoryItem> {
        return when (query.market) {
            StockOrderMarket.DOMESTIC -> fetchDomesticOrderHistory(query)
            StockOrderMarket.OVERSEAS_US -> fetchOverseasOrderHistory(query)
        }
    }

    private fun fetchDomesticOrderHistory(query: BrokerOrderHistoryQuery): List<BrokerOrderHistoryItem> {
        val response = stockApiClient.getExternalApi(
            uri = OPEN_API_PREFIX + GET_EXECUTION_ORDERS,
            queryParameters = query.toKisDomesticExecutionOrderQuery(),
            responseType = DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersResponse::class.java,
            accept = MediaType.APPLICATION_PROTOBUF,
        )
        if (response.rtCd.isNotBlank() && response.rtCd != "0") {
            throw RuntimeException("domestic execution lookup failed: ${response.msgCd} ${response.msg1}".trim())
        }
        return response.output1List.mapNotNull { it.toBrokerHistoryItem() }
    }

    private fun fetchOverseasOrderHistory(query: BrokerOrderHistoryQuery): List<BrokerOrderHistoryItem> {
        val response = stockApiClient.getExternalApi(
            uri = OPEN_API_PREFIX + GET_OVERSEAS_EXECUTION_ORDERS,
            queryParameters = query.toKisOverseasExecutionOrderQuery(),
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
        return rows.mapNotNull { it.toBrokerHistoryItem() }
    }
}

internal const val OPEN_API_PREFIX = "/open-api"

internal fun BrokerOrderCommand.toKisDomesticOrderRequest(): Map<String, Any> {
    return mapOf(
        "PDNO" to symbol,
        "ORD_DVSN" to orderType.toKisDomesticOrderDivision(),
        "ORD_QTY" to quantity,
        "ORD_UNPR" to price.toLong(),
        "EXCG_ID_DVSN_CD" to "KRX",
        "SLL_TYPE" to if (side == OrderIntentSide.SELL) "01" else "",
        "isMock" to isMock,
    )
}

internal fun BrokerOrderCommand.toKisUsOverseasOrderRequest(): Map<String, Any> {
    val baseRequest = mapOf(
        "PDNO" to symbol.uppercase(),
        "OVRS_EXCG_CD" to exchangeForUsStock(symbol),
        "ORD_QTY" to quantity,
        "OVRS_ORD_UNPR" to price.toBrokerPrice(),
        "ORD_DVSN" to orderType.toKisUsOrderDivision(side, isMock),
        "CTAC_TLNO" to "",
        "MGCO_APTM_ODNO" to "",
        "ORD_SVR_DVSN_CD" to "0",
        "isMock" to isMock,
    )
    return if (side == OrderIntentSide.SELL) {
        baseRequest + ("SLL_TYPE" to "00")
    } else {
        baseRequest
    }
}

private fun BrokerOrderHistoryQuery.toKisDomesticExecutionOrderQuery(): Map<String, String> {
    val yyyymmdd = date.format(DateTimeFormatter.BASIC_ISO_DATE)
    return mapOf(
        "isMock" to isMock.toString(),
        "inqrStrtDt" to yyyymmdd,
        "inqrEndDt" to yyyymmdd,
        "sllBuyDvsnCd" to "00",
        "inqrDvsn" to "00",
        "pdno" to symbol,
        "ccldDvsn" to "00",
        "ordGnoBrno" to "",
        "odno" to externalOrderId,
        "inqrDvsn3" to "00",
        "inqrDvsn1" to "",
        "ctxAreaFk100" to "",
        "ctxAreaNk100" to "",
    )
}

private fun BrokerOrderHistoryQuery.toKisOverseasExecutionOrderQuery(): Map<String, String> {
    val yyyymmdd = date.format(DateTimeFormatter.BASIC_ISO_DATE)
    return mapOf(
        "isMock" to isMock.toString(),
        "pdno" to if (isMock) "" else symbol.ifBlank { "%" }.uppercase(),
        "ordStrtDt" to yyyymmdd,
        "ordEndDt" to yyyymmdd,
        "sllBuyDvsn" to "00",
        "ccldNccsDvsn" to "00",
        "ovrsExcgCd" to if (isMock) "" else "NASD",
        "sortSqn" to "DS",
        "ordDt" to "",
        "ordGnoBrno" to "",
        "odno" to "",
        "ctxAreaNk200" to "",
        "ctxAreaFk200" to "",
    )
}

private fun DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1.toBrokerHistoryItem(): BrokerOrderHistoryItem? {
    val orderId = odno.trim()
    if (orderId.isBlank()) return null
    return BrokerOrderHistoryItem(
        externalOrderId = orderId,
        branchOrderNumber = ordGnoBrno.takeIf { it.isNotBlank() },
        symbol = pdno.trim(),
        stockName = prdtName.trim(),
        orderedAt = parseKisOrderDateTime(ordDt, ordTmd),
        orderedQuantity = ordQty.toLongValue(),
        cumulativeFilledQuantity = totCcldQty.toLongValue(),
        remainingQuantity = rmnQty.toLongValue(),
        rejectedQuantity = rjctQty.toLongValue(),
        cancelledQuantity = cnclCfrmQty.toLongValue(),
        cancelled = cnclYn.equals("Y", ignoreCase = true),
        side = sllBuyDvsnCd.toOrderIntentSide(),
        averageExecutionPrice = avgPrvs.toDoubleValue(),
    )
}

private fun JsonNode.toBrokerHistoryItem(): BrokerOrderHistoryItem? {
    val orderId = path("odno").asText("").trim()
    if (orderId.isBlank()) return null
    val orderedQuantity = path("ft_ord_qty").asText("").toLongValue()
    val filledQuantity = path("ft_ccld_qty").asText("").toLongValue()
    val remainingQuantity = path("nccs_qty").asText("").toLongValue()
    val statusName = path("prcs_stat_name").asText("").takeIf { it.isNotBlank() }
    val rejectionReason = path("rjct_rson").asText("").takeIf { it.isNotBlank() }
    return BrokerOrderHistoryItem(
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

private fun WebClient.submitStockOrder(
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

internal fun parseKisOrderDateTime(date: String, time: String): ZonedDateTime {
    val parsedDate = date.takeIf { it.length == 8 }?.let {
        LocalDate.of(
            it.substring(0, 4).toInt(),
            it.substring(4, 6).toInt(),
            it.substring(6, 8).toInt(),
        )
    } ?: LocalDate.now(BROKER_ORDER_ZONE)
    val normalizedTime = time.filter(Char::isDigit).padEnd(6, '0').take(6)
    val parsedTime = LocalTime.of(
        normalizedTime.substring(0, 2).toIntOrNull() ?: 0,
        normalizedTime.substring(2, 4).toIntOrNull() ?: 0,
        normalizedTime.substring(4, 6).toIntOrNull() ?: 0,
    )
    return ZonedDateTime.of(parsedDate, parsedTime, BROKER_ORDER_ZONE)
}

internal fun String?.toLongValue(): Long {
    return this?.replace(",", "")?.trim()?.toBigDecimalOrNull()?.toLong() ?: 0L
}

internal fun String?.toDoubleValue(): Double? {
    return this?.replace(",", "")?.trim()?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
}

internal fun String?.toOrderIntentSide(): OrderIntentSide? {
    return when (this?.trim()) {
        "01" -> OrderIntentSide.SELL
        "02" -> OrderIntentSide.BUY
        else -> null
    }
}

private fun StockOrderType.toKisDomesticOrderDivision(): String {
    return when (this) {
        StockOrderType.LIMIT,
        StockOrderType.LOC -> "00"

        StockOrderType.MOC -> "01"
    }
}

private fun StockOrderType.toKisUsOrderDivision(side: OrderIntentSide, isMock: Boolean): String {
    if (isMock) return "00"

    return when (side) {
        OrderIntentSide.BUY -> when (this) {
            StockOrderType.LIMIT -> "00"
            StockOrderType.LOC -> "34"
            StockOrderType.MOC -> throw UnsupportedOperationException("US overseas buy does not support MOC")
        }

        OrderIntentSide.SELL -> when (this) {
            StockOrderType.LIMIT -> "00"
            StockOrderType.LOC -> "34"
            StockOrderType.MOC -> "33"
        }
    }
}

private fun exchangeForUsStock(symbol: String): String {
    return when (symbol.uppercase()) {
        "TQQQ", "SOXL" -> "NASD"
        else -> "NASD"
    }
}

private fun Double.toBrokerPrice(): String {
    return BigDecimal.valueOf(this)
        .stripTrailingZeros()
        .toPlainString()
}

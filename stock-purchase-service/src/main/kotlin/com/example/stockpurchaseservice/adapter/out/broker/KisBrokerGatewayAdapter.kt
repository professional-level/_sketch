package com.example.stockpurchaseservice.adapter.out.broker

import ApiResponse
import DailyExecutionOrdersResponseOuterClass
import com.example.common.ExternalApiAdapter
import com.example.common.endpoint.Endpoint.GET_EXECUTION_ORDERS
import com.example.common.endpoint.Endpoint.GET_OVERSEAS_EXECUTION_ORDERS
import com.example.common.endpoint.Endpoint.POST_OVERSEAS_STOCK_ORDER_CANCEL
import com.example.common.endpoint.Endpoint.POST_OVERSEAS_STOCK_ORDER
import com.example.common.endpoint.Endpoint.POST_STOCK_ORDER_CANCEL
import com.example.common.endpoint.Endpoint.POST_STOCK_ORDER
import com.example.stockpurchaseservice.adapter.out.api.ExternalApiCallOptions
import com.example.stockpurchaseservice.adapter.out.api.awaitExternalApi
import com.example.stockpurchaseservice.adapter.out.api.getExternalApi
import com.example.stockpurchaseservice.adapter.out.api.isTransientExternalApiFailure
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderRejectedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionUnknownException
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import com.example.stockpurchaseservice.config.broker.KisBrokerGatewayProperties
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
    private val properties: KisBrokerGatewayProperties = KisBrokerGatewayProperties(),
    private val guard: KisBrokerGatewayGuard = KisBrokerGatewayGuard(properties),
) : BrokerGateway {

    override fun submitOrder(command: BrokerOrderCommand): BrokerOrderSubmissionDto {
        return guard.execute("submit-order:${command.market}") {
            when (command.market) {
                StockOrderMarket.DOMESTIC -> stockApiClient.submitStockOrder(
                    uri = OPEN_API_PREFIX + POST_STOCK_ORDER,
                    body = command.toKisDomesticOrderRequest(),
                    callOptions = properties.toSubmitCallOptions(),
                )

                StockOrderMarket.OVERSEAS_US -> stockApiClient.submitStockOrder(
                    uri = OPEN_API_PREFIX + POST_OVERSEAS_STOCK_ORDER,
                    body = command.toKisUsOverseasOrderRequest(),
                    callOptions = properties.toSubmitCallOptions(),
                )
            }
        }
    }

    override fun cancelOrder(command: BrokerOrderCancelCommand): BrokerOrderSubmissionDto {
        return guard.execute("cancel-order:${command.market}") {
            when (command.market) {
                StockOrderMarket.DOMESTIC -> stockApiClient.submitStockOrder(
                    uri = OPEN_API_PREFIX + POST_STOCK_ORDER_CANCEL,
                    body = command.toKisDomesticCancelRequest(),
                    callOptions = properties.toSubmitCallOptions(),
                )

                StockOrderMarket.OVERSEAS_US -> stockApiClient.submitStockOrder(
                    uri = OPEN_API_PREFIX + POST_OVERSEAS_STOCK_ORDER_CANCEL,
                    body = command.toKisUsOverseasCancelRequest(),
                    callOptions = properties.toSubmitCallOptions(),
                )
            }
        }
    }

    override fun findOrderHistory(query: BrokerOrderHistoryQuery): List<BrokerOrderHistoryItem> {
        val items = mutableListOf<BrokerOrderHistoryItem>()
        var cursor = query.pageCursor
        val seenCursors = mutableSetOf<BrokerOrderHistoryPageCursor>()
        var pageCount = 0
        do {
            if (!seenCursors.add(cursor)) break
            val page = when (query.market) {
                StockOrderMarket.DOMESTIC -> fetchDomesticOrderHistoryPage(query.copy(pageCursor = cursor))
                StockOrderMarket.OVERSEAS_US -> fetchOverseasOrderHistoryPage(query.copy(pageCursor = cursor))
            }
            items += page.items
            cursor = page.nextCursor
            pageCount += 1
        } while (cursor.hasNext() && pageCount < MAX_HISTORY_PAGES)

        return items
    }

    private fun fetchDomesticOrderHistoryPage(query: BrokerOrderHistoryQuery): BrokerOrderHistoryPage {
        return guard.execute("domestic-order-history") {
            val response = stockApiClient.getExternalApi(
                uri = OPEN_API_PREFIX + GET_EXECUTION_ORDERS,
                queryParameters = query.toKisDomesticExecutionOrderQuery(),
                responseType = DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersResponse::class.java,
                accept = MediaType.APPLICATION_PROTOBUF,
                callOptions = properties.toQueryCallOptions(),
            )
            if (response.rtCd.isNotBlank() && response.rtCd != "0") {
                throw RuntimeException("domestic execution lookup failed: ${response.msgCd} ${response.msg1}".trim())
            }
            BrokerOrderHistoryPage(
                items = response.output1List.mapNotNull { it.toBrokerHistoryItem() },
                nextCursor = BrokerOrderHistoryPageCursor(
                    foreignKeyContext = response.ctxAreaFk100,
                    nextKeyContext = response.ctxAreaNk100,
                ),
            )
        }
    }

    private fun fetchOverseasOrderHistoryPage(query: BrokerOrderHistoryQuery): BrokerOrderHistoryPage {
        return guard.execute("overseas-order-history") {
            val response = stockApiClient.getExternalApi(
                uri = OPEN_API_PREFIX + GET_OVERSEAS_EXECUTION_ORDERS,
                queryParameters = query.toKisOverseasExecutionOrderQuery(),
                responseType = JsonNode::class.java,
                callOptions = properties.toQueryCallOptions(),
            )
            val returnCode = response.path("rt_cd").asText("")
            if (returnCode.isNotBlank() && returnCode != "0") {
                throw RuntimeException(
                    "overseas execution lookup failed: ${
                        response.path("msg_cd").asText()
                    } ${response.path("msg1").asText()}".trim(),
                )
            }
            val output = response.path("output")
            val rows = when {
                output.isArray -> output.toList()
                output.isObject -> listOf(output)
                else -> emptyList()
            }
            BrokerOrderHistoryPage(
                items = rows.mapNotNull { it.toBrokerHistoryItem() },
                nextCursor = BrokerOrderHistoryPageCursor(
                    foreignKeyContext = response.path("ctx_area_fk200").asText(""),
                    nextKeyContext = response.path("ctx_area_nk200").asText(""),
                ),
            )
        }
    }

    companion object {
        private const val MAX_HISTORY_PAGES = 20
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

internal fun BrokerOrderCancelCommand.toKisDomesticCancelRequest(): Map<String, Any> {
    val branchOrderNumber = requireNotNull(branchOrderNumber?.takeIf { it.isNotBlank() }) {
        "domestic cancel requires branch order number"
    }
    return mapOf(
        "KRX_FWDG_ORD_ORGNO" to branchOrderNumber,
        "ORGN_ODNO" to originalOrderId,
        "ORD_DVSN" to orderType.toKisDomesticOrderDivision(),
        "RVSE_CNCL_DVSN_CD" to "02",
        "ORD_QTY" to quantity,
        "ORD_UNPR" to price.toLong(),
        "QTY_ALL_ORD_YN" to if (cancelAll) "Y" else "N",
        "EXCG_ID_DVSN_CD" to "KRX",
        "CNDT_PRIC" to "",
        "isMock" to isMock,
    )
}

internal fun BrokerOrderCancelCommand.toKisUsOverseasCancelRequest(): Map<String, Any> {
    return mapOf(
        "OVRS_EXCG_CD" to exchangeForUsStock(symbol),
        "PDNO" to symbol.uppercase(),
        "ORGN_ODNO" to originalOrderId,
        "RVSE_CNCL_DVSN_CD" to "02",
        "ORD_QTY" to quantity,
        "OVRS_ORD_UNPR" to price.toBrokerPrice(),
        "MGCO_APTM_ODNO" to "",
        "ORD_SVR_DVSN_CD" to "0",
        "isMock" to isMock,
    )
}

private fun BrokerOrderHistoryQuery.toKisDomesticExecutionOrderQuery(): Map<String, String> {
    return mapOf(
        "isMock" to isMock.toString(),
        "inqrStrtDt" to from.toKisDate(),
        "inqrEndDt" to to.toKisDate(),
        "sllBuyDvsnCd" to "00",
        "inqrDvsn" to "00",
        "pdno" to symbol,
        "ccldDvsn" to "00",
        "ordGnoBrno" to "",
        "odno" to externalOrderId,
        "inqrDvsn3" to "00",
        "inqrDvsn1" to "",
        "ctxAreaFk100" to pageCursor.foreignKeyContext,
        "ctxAreaNk100" to pageCursor.nextKeyContext,
    )
}

internal fun BrokerOrderHistoryQuery.toKisOverseasExecutionOrderQuery(): Map<String, String> {
    return mapOf(
        "isMock" to isMock.toString(),
        "pdno" to if (isMock) "" else symbol.ifBlank { "%" }.uppercase(),
        "ordStrtDt" to from.toKisDate(),
        "ordEndDt" to to.toKisDate(),
        "sllBuyDvsn" to "00",
        "ccldNccsDvsn" to "00",
        "ovrsExcgCd" to if (isMock) "" else "NASD",
        "sortSqn" to "DS",
        "ordDt" to "",
        "ordGnoBrno" to "",
        // KIS overseas inquire-ccnl documents ODNO as non-searchable; match by order id client-side.
        "odno" to "",
        "ctxAreaNk200" to pageCursor.nextKeyContext,
        "ctxAreaFk200" to pageCursor.foreignKeyContext,
    )
}

private fun KisBrokerGatewayProperties.toQueryCallOptions(): ExternalApiCallOptions {
    return ExternalApiCallOptions(
        timeout = requestTimeout,
        maxAttempts = queryMaxAttempts,
        backoff = queryBackoff,
        transientHttpStatuses = transientHttpStatuses,
    )
}

private fun KisBrokerGatewayProperties.toSubmitCallOptions(): ExternalApiCallOptions {
    return ExternalApiCallOptions(
        timeout = requestTimeout,
        maxAttempts = 1,
        transientHttpStatuses = transientHttpStatuses,
    )
}

private fun ZonedDateTime.toKisDate(): String {
    return toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)
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
    val statusName = path("prcs_stat_name").textOrNull()
    val revisionCancelName = path("rvse_cncl_dvsn_name").textOrNull()
    val statusMessage = joinedText(statusName, revisionCancelName)
    val rejectionReason = joinedText(
        path("rjct_rson").textOrNull(),
        path("rjct_rson_name").textOrNull(),
    )
    val cancelled = statusMessage?.contains(CANCELLED_KOREAN) == true
    return BrokerOrderHistoryItem(
        externalOrderId = orderId,
        branchOrderNumber = path("ord_gno_brno").textOrNull(),
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
        cancelledQuantity = if (cancelled) remainingQuantity else 0,
        cancelled = cancelled,
        side = path("sll_buy_dvsn_cd").asText("").toOrderIntentSide(),
        averageExecutionPrice = path("ft_ccld_unpr3").asText("").toDoubleValue(),
        statusMessage = statusMessage,
        rejectionReason = rejectionReason,
    )
}

private fun JsonNode.textOrNull(): String? {
    return asText("").trim().takeIf { it.isNotBlank() }
}

private fun joinedText(vararg values: String?): String? {
    return values
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .joinToString("; ")
        .takeIf(String::isNotBlank)
}

private fun WebClient.submitStockOrder(
    uri: String,
    body: Map<String, Any>,
    callOptions: ExternalApiCallOptions,
): BrokerOrderSubmissionDto {
    val response = try {
        post()
            .uri(uri)
            .accept(MediaType.APPLICATION_PROTOBUF)
            .bodyValue(body)
            .retrieve()
            .toEntity(ApiResponse.StockOrder::class.java)
            .awaitExternalApi(callOptions)
    } catch (exception: Throwable) {
        if (exception.isTransientExternalApiFailure(callOptions.transientHttpStatuses)) {
            throw BrokerOrderSubmissionUnknownException(
                message = "stock order submission status unknown after transient broker failure: " +
                    (exception.message ?: exception::class.java.simpleName),
                cause = exception,
            )
        }
        throw exception
    } ?: throw BrokerOrderSubmissionUnknownException("stock order response is empty")

    val order = response.body ?: throw BrokerOrderSubmissionUnknownException("stock order body is empty")
    if (order.rtCd != "0") {
        throw BrokerOrderRejectedException(
            message = "stock order rejected by broker: ${order.msgCd} ${order.msg1}".trim(),
            brokerReturnCode = order.rtCd,
            brokerMessageCode = order.msgCd.takeIf { it.isNotBlank() },
        )
    }

    val externalOrderId = order.output.getODNO().takeIf { it.isNotBlank() }
        ?: throw BrokerOrderSubmissionUnknownException(
            message = "stock order accepted but broker order id is missing",
        )
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

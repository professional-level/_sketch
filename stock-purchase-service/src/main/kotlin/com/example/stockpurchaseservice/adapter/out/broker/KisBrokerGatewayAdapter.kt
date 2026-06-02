package com.example.stockpurchaseservice.adapter.out.broker

import ApiResponse
import DailyExecutionOrdersResponseOuterClass
import com.example.common.ExternalApiAdapter
import com.example.common.endpoint.Endpoint.GET_EXECUTION_ORDERS
import com.example.common.endpoint.Endpoint.GET_OVERSEAS_EXECUTION_ORDERS
import com.example.common.endpoint.Endpoint.GET_OVERSEAS_STOCK_BALANCE
import com.example.common.endpoint.Endpoint.GET_STOCK_BALANCE
import com.example.common.endpoint.Endpoint.GET_STOCK_ORDER_CANCELABLE
import com.example.common.endpoint.Endpoint.POST_OVERSEAS_STOCK_ORDER_CANCEL
import com.example.common.endpoint.Endpoint.POST_OVERSEAS_STOCK_ORDER
import com.example.common.endpoint.Endpoint.POST_STOCK_ORDER_CANCEL
import com.example.common.endpoint.Endpoint.POST_STOCK_ORDER
import com.example.stockpurchaseservice.adapter.out.api.ExternalApiCallOptions
import com.example.stockpurchaseservice.adapter.out.api.awaitExternalApi
import com.example.stockpurchaseservice.adapter.out.api.getExternalApi
import com.example.stockpurchaseservice.adapter.out.api.isTransientExternalApiFailure
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderQueryFailedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderRejectedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionUnknownException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderTemporaryUnavailableException
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
        return when (command.market) {
            StockOrderMarket.DOMESTIC -> {
                ensureDomesticOrderCancelable(command)
                guard.execute("cancel-order:${command.market}") {
                    stockApiClient.submitStockOrder(
                        uri = OPEN_API_PREFIX + POST_STOCK_ORDER_CANCEL,
                        body = command.toKisDomesticCancelRequest(),
                        callOptions = properties.toSubmitCallOptions(),
                    )
                }
            }

            StockOrderMarket.OVERSEAS_US -> {
                ensureOverseasOrderCancelable(command)
                guard.execute("cancel-order:${command.market}") {
                    stockApiClient.submitStockOrder(
                        uri = OPEN_API_PREFIX + POST_OVERSEAS_STOCK_ORDER_CANCEL,
                        body = command.toKisUsOverseasCancelRequest(),
                        callOptions = properties.toSubmitCallOptions(),
                    )
                }
            }
        }
    }

    private fun ensureDomesticOrderCancelable(command: BrokerOrderCancelCommand) {
        val cancelableOrder = findDomesticCancelableOrders(command)
            .firstOrNull { it.matches(command) }
            ?: throw BrokerOrderRejectedException(
                message = "domestic order is not cancelable: ${command.originalOrderId}",
            )
        if (cancelableOrder.possibleQuantity < command.quantity) {
            throw BrokerOrderRejectedException(
                message = "domestic order cancel quantity exceeds possible quantity: " +
                    "requested=${command.quantity} possible=${cancelableOrder.possibleQuantity}",
            )
        }
    }

    private fun findDomesticCancelableOrders(command: BrokerOrderCancelCommand): List<KisCancelableOrderItem> {
        val items = mutableListOf<KisCancelableOrderItem>()
        var cursor = BrokerOrderHistoryPageCursor.EMPTY
        val seenCursors = mutableSetOf<BrokerOrderHistoryPageCursor>()
        var pageCount = 0
        do {
            if (!seenCursors.add(cursor)) break
            val page = guard.executeQuery("domestic-cancelable-order") {
                stockApiClient.fetchDomesticCancelableOrderPage(command, cursor, properties.toQueryCallOptions())
            }
            items += page.items
            cursor = page.nextCursor
            pageCount += 1
        } while (cursor.hasNext() && pageCount < MAX_CANCELABLE_ORDER_PAGES)

        return items
    }

    private fun ensureOverseasOrderCancelable(command: BrokerOrderCancelCommand) {
        val order = findOrderHistory(
            BrokerOrderHistoryQuery(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = command.symbol,
                externalOrderId = command.originalOrderId,
                isMock = command.isMock,
            ),
        ).firstOrNull { it.matches(command) }
            ?: throw BrokerOrderRejectedException(
                message = "overseas order is not cancelable: ${command.originalOrderId}",
            )

        val status = order.toStatus()
        when (status.status) {
            BrokerOrderStatus.REJECTED,
            BrokerOrderStatus.CANCELLED -> {
                val reason = status.reason?.let { " reason=$it" }.orEmpty()
                throw BrokerOrderRejectedException(
                    message = "overseas order is not cancelable: " +
                        "${command.originalOrderId} status=${status.status}$reason",
                )
            }

            BrokerOrderStatus.SUBMITTED,
            BrokerOrderStatus.UNKNOWN -> Unit
        }

        if (order.remainingQuantity <= 0) {
            throw BrokerOrderRejectedException(
                message = "overseas order has no cancelable quantity: ${command.originalOrderId}",
            )
        }
        if (order.remainingQuantity < command.quantity) {
            throw BrokerOrderRejectedException(
                message = "overseas order cancel quantity exceeds remaining quantity: " +
                    "requested=${command.quantity} remaining=${order.remainingQuantity}",
            )
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

    override fun findAccountSnapshot(query: BrokerAccountSnapshotQuery): BrokerAccountSnapshot {
        return when (query.market) {
            StockOrderMarket.DOMESTIC -> findDomesticAccountSnapshot(query)
            StockOrderMarket.OVERSEAS_US -> findOverseasAccountSnapshot(query)
        }
    }

    private fun findDomesticAccountSnapshot(query: BrokerAccountSnapshotQuery): BrokerAccountSnapshot {
        val positions = mutableListOf<BrokerPositionSnapshot>()
        var summary = BrokerAccountSnapshot(
            market = StockOrderMarket.DOMESTIC,
            exchange = DOMESTIC_EXCHANGE,
            currency = DOMESTIC_CURRENCY,
            positions = emptyList(),
        )
        var cursor = query.pageCursor
        val seenCursors = mutableSetOf<BrokerOrderHistoryPageCursor>()
        var pageCount = 0
        do {
            if (!seenCursors.add(cursor)) break
            val page = fetchDomesticAccountSnapshotPage(query.copy(pageCursor = cursor))
            positions += page.snapshot.positions
            summary = summary.mergeSummaryFrom(page.snapshot)
            cursor = page.nextCursor
            pageCount += 1
        } while (cursor.hasNext() && pageCount < MAX_ACCOUNT_BALANCE_PAGES)

        return summary.copy(positions = positions)
    }

    private fun findOverseasAccountSnapshot(query: BrokerAccountSnapshotQuery): BrokerAccountSnapshot {
        val positions = mutableListOf<BrokerPositionSnapshot>()
        var summary = BrokerAccountSnapshot(
            market = StockOrderMarket.OVERSEAS_US,
            exchange = query.exchange,
            currency = query.currency,
            positions = emptyList(),
        )
        var cursor = query.pageCursor
        val seenCursors = mutableSetOf<BrokerOrderHistoryPageCursor>()
        var pageCount = 0
        do {
            if (!seenCursors.add(cursor)) break
            val page = fetchOverseasAccountSnapshotPage(query.copy(pageCursor = cursor))
            positions += page.snapshot.positions
            summary = summary.mergeSummaryFrom(page.snapshot)
            cursor = page.nextCursor
            pageCount += 1
        } while (cursor.hasNext() && pageCount < MAX_ACCOUNT_BALANCE_PAGES)

        return summary.copy(positions = positions)
    }

    private fun fetchDomesticOrderHistoryPage(query: BrokerOrderHistoryQuery): BrokerOrderHistoryPage {
        return guard.executeQuery("domestic-order-history") {
            val response = stockApiClient.getExternalApi(
                uri = OPEN_API_PREFIX + GET_EXECUTION_ORDERS,
                queryParameters = query.toKisDomesticExecutionOrderQuery(),
                responseType = DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersResponse::class.java,
                accept = MediaType.APPLICATION_PROTOBUF,
                callOptions = properties.toQueryCallOptions(),
            )
            if (response.rtCd.isNotBlank() && response.rtCd != "0") {
                throwKisBrokerBusinessFailure(
                    operation = "domestic execution lookup",
                    returnCode = response.rtCd,
                    messageCode = response.msgCd,
                    brokerMessage = response.msg1,
                )
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
        return guard.executeQuery("overseas-order-history") {
            val response = stockApiClient.getExternalApi(
                uri = OPEN_API_PREFIX + GET_OVERSEAS_EXECUTION_ORDERS,
                queryParameters = query.toKisOverseasExecutionOrderQuery(),
                responseType = JsonNode::class.java,
                callOptions = properties.toQueryCallOptions(),
            )
            val returnCode = response.textOrNull("rt_cd", "rtCd", "RT_CD").orEmpty()
            if (returnCode.isNotBlank() && returnCode != "0") {
                throwKisBrokerBusinessFailure(
                    operation = "overseas execution lookup",
                    returnCode = returnCode,
                    messageCode = response.textOrNull("msg_cd", "msgCd", "MSG_CD"),
                    brokerMessage = response.textOrNull("msg1", "msg_1", "MSG1"),
                )
            }
            BrokerOrderHistoryPage(
                items = response.rows("output", "OUTPUT", "output1", "OUTPUT1").mapNotNull { it.toBrokerHistoryItem() },
                nextCursor = BrokerOrderHistoryPageCursor(
                    foreignKeyContext = response.textOrNull(
                        "ctx_area_fk200",
                        "ctxAreaFk200",
                        "CTX_AREA_FK200",
                    ).orEmpty(),
                    nextKeyContext = response.textOrNull(
                        "ctx_area_nk200",
                        "ctxAreaNk200",
                        "CTX_AREA_NK200",
                    ).orEmpty(),
                ),
            )
        }
    }

    private fun fetchDomesticAccountSnapshotPage(query: BrokerAccountSnapshotQuery): BrokerAccountSnapshotPage {
        return guard.executeQuery("domestic-account-balance") {
            val response = stockApiClient.getExternalApi(
                uri = OPEN_API_PREFIX + GET_STOCK_BALANCE,
                queryParameters = query.toKisDomesticBalanceQuery(),
                responseType = JsonNode::class.java,
                callOptions = properties.toQueryCallOptions(),
            )
            val returnCode = response.textOrNull("rt_cd", "rtCd", "RT_CD").orEmpty()
            if (returnCode.isNotBlank() && returnCode != "0") {
                throwKisBrokerBusinessFailure(
                    operation = "domestic balance lookup",
                    returnCode = returnCode,
                    messageCode = response.textOrNull("msg_cd", "msgCd", "MSG_CD"),
                    brokerMessage = response.textOrNull("msg1", "msg_1", "MSG1"),
                )
            }
            BrokerAccountSnapshotPage(
                snapshot = response.toDomesticBrokerAccountSnapshot(),
                nextCursor = BrokerOrderHistoryPageCursor(
                    foreignKeyContext = response.textOrNull("ctx_area_fk100", "CTX_AREA_FK100").orEmpty(),
                    nextKeyContext = response.textOrNull("ctx_area_nk100", "CTX_AREA_NK100").orEmpty(),
                ),
            )
        }
    }

    private fun fetchOverseasAccountSnapshotPage(query: BrokerAccountSnapshotQuery): BrokerAccountSnapshotPage {
        return guard.executeQuery("overseas-account-balance") {
            val response = stockApiClient.getExternalApi(
                uri = OPEN_API_PREFIX + GET_OVERSEAS_STOCK_BALANCE,
                queryParameters = query.toKisOverseasBalanceQuery(),
                responseType = JsonNode::class.java,
                callOptions = properties.toQueryCallOptions(),
            )
            val returnCode = response.textOrNull("rt_cd", "rtCd", "RT_CD").orEmpty()
            if (returnCode.isNotBlank() && returnCode != "0") {
                throwKisBrokerBusinessFailure(
                    operation = "overseas balance lookup",
                    returnCode = returnCode,
                    messageCode = response.textOrNull("msg_cd", "msgCd", "MSG_CD"),
                    brokerMessage = response.textOrNull("msg1", "msg_1", "MSG1"),
                )
            }
            BrokerAccountSnapshotPage(
                snapshot = response.toBrokerAccountSnapshot(query),
                nextCursor = BrokerOrderHistoryPageCursor(
                    foreignKeyContext = response.textOrNull("ctx_area_fk200", "CTX_AREA_FK200").orEmpty(),
                    nextKeyContext = response.textOrNull("ctx_area_nk200", "CTX_AREA_NK200").orEmpty(),
                ),
            )
        }
    }

    companion object {
        private const val MAX_HISTORY_PAGES = 20
        private const val MAX_CANCELABLE_ORDER_PAGES = 10
        private const val MAX_ACCOUNT_BALANCE_PAGES = 20
    }
}

internal const val OPEN_API_PREFIX = "/open-api"
private const val DOMESTIC_EXCHANGE = "KRX"
private const val DOMESTIC_CURRENCY = "KRW"
private const val KIS_CANCEL_REVISION_CODE = "02"

private val TEMPORARY_KIS_MESSAGE_CODES = setOf(
    "EGW00201",
)

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

private fun BrokerOrderCancelCommand.toKisDomesticCancelableOrderQuery(
    cursor: BrokerOrderHistoryPageCursor = BrokerOrderHistoryPageCursor.EMPTY,
): Map<String, String> {
    return mapOf(
        "isMock" to isMock.toString(),
        "inqrDvsn1" to "1",
        "inqrDvsn2" to "0",
        "ctxAreaFk100" to cursor.foreignKeyContext,
        "ctxAreaNk100" to cursor.nextKeyContext,
    )
}

private fun WebClient.fetchDomesticCancelableOrderPage(
    command: BrokerOrderCancelCommand,
    cursor: BrokerOrderHistoryPageCursor,
    callOptions: ExternalApiCallOptions,
): KisCancelableOrderPage {
    val response = getExternalApi(
        uri = OPEN_API_PREFIX + GET_STOCK_ORDER_CANCELABLE,
        queryParameters = command.toKisDomesticCancelableOrderQuery(cursor),
        responseType = JsonNode::class.java,
        callOptions = callOptions,
    )
    val returnCode = response.textOrNull("rt_cd", "rtCd", "RT_CD").orEmpty()
    if (returnCode.isNotBlank() && returnCode != "0") {
        throwKisBrokerBusinessFailure(
            operation = "domestic cancelable order lookup",
            returnCode = returnCode,
            messageCode = response.textOrNull("msg_cd", "msgCd", "MSG_CD"),
            brokerMessage = response.textOrNull("msg1", "msg_1", "MSG1"),
        )
    }
    val rows = response.rows("output", "OUTPUT", "output1", "OUTPUT1")
    return KisCancelableOrderPage(
        items = rows.mapNotNull { it.toKisCancelableOrderItem() },
        nextCursor = BrokerOrderHistoryPageCursor(
            foreignKeyContext = response.textOrNull("ctx_area_fk100", "ctxAreaFk100", "CTX_AREA_FK100").orEmpty(),
            nextKeyContext = response.textOrNull("ctx_area_nk100", "ctxAreaNk100", "CTX_AREA_NK100").orEmpty(),
        ),
    )
}

private data class KisCancelableOrderPage(
    val items: List<KisCancelableOrderItem>,
    val nextCursor: BrokerOrderHistoryPageCursor,
)

private data class KisBrokerBusinessFailure(
    val operation: String,
    val returnCode: String,
    val messageCode: String?,
    val brokerMessage: String?,
) {
    val message: String =
        "$operation failed: ${listOfNotNull(messageCode, brokerMessage).joinToString(" ")}"
            .trim()

    fun toException(): RuntimeException {
        if (messageCode in TEMPORARY_KIS_MESSAGE_CODES) {
            return BrokerOrderTemporaryUnavailableException(
                message = message,
                brokerReturnCode = returnCode,
                brokerMessageCode = messageCode,
                brokerMessage = brokerMessage,
            )
        }
        return BrokerOrderQueryFailedException(
            message = message,
            brokerReturnCode = returnCode,
            brokerMessageCode = messageCode,
            brokerMessage = brokerMessage,
        )
    }
}

private fun throwKisBrokerBusinessFailure(
    operation: String,
    returnCode: String,
    messageCode: String?,
    brokerMessage: String?,
): Nothing {
    throw KisBrokerBusinessFailure(
        operation = operation,
        returnCode = returnCode,
        messageCode = messageCode?.takeIf { it.isNotBlank() },
        brokerMessage = brokerMessage?.takeIf { it.isNotBlank() },
    ).toException()
}

private data class BrokerAccountSnapshotPage(
    val snapshot: BrokerAccountSnapshot,
    val nextCursor: BrokerOrderHistoryPageCursor,
)

private data class KisCancelableOrderItem(
    val branchOrderNumber: String?,
    val orderId: String,
    val originalOrderId: String?,
    val symbol: String,
    val possibleQuantity: Long,
) {
    fun matches(command: BrokerOrderCancelCommand): Boolean {
        val sameOrder = orderId == command.originalOrderId || originalOrderId == command.originalOrderId
        val sameBranch = command.branchOrderNumber.isNullOrBlank() || branchOrderNumber == command.branchOrderNumber
        val sameSymbol = symbol.isBlank() || symbol.equals(command.symbol, ignoreCase = true)
        return sameOrder && sameBranch && sameSymbol
    }
}

private fun JsonNode.toKisCancelableOrderItem(): KisCancelableOrderItem? {
    val orderId = textOrNull("odno", "ODNO", "ord_no", "ORD_NO", "order_no").orEmpty()
    val originalOrderId = textOrNull("orgn_odno", "ORGN_ODNO")
    val matchableOrderId = orderId.ifBlank { originalOrderId.orEmpty() }
    if (matchableOrderId.isBlank()) return null
    return KisCancelableOrderItem(
        branchOrderNumber = textOrNull("ord_gno_brno", "ORD_GNO_BRNO", "krx_fwdg_ord_orgno", "KRX_FWDG_ORD_ORGNO"),
        orderId = orderId,
        originalOrderId = originalOrderId,
        symbol = textOrNull("pdno", "PDNO").orEmpty(),
        possibleQuantity = longValue("psbl_qty", "PSBL_QTY", "ord_psbl_qty", "ORD_PSBL_QTY"),
    )
}

private fun BrokerOrderHistoryItem.matches(command: BrokerOrderCancelCommand): Boolean {
    val sameOrder = externalOrderId == command.originalOrderId
    val sameSymbol = symbol.isBlank() || symbol.equals(command.symbol, ignoreCase = true)
    return sameOrder && sameSymbol
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

private fun BrokerAccountSnapshotQuery.toKisOverseasBalanceQuery(): Map<String, String> {
    return mapOf(
        "isMock" to isMock.toString(),
        "ovrsExcgCd" to exchange.uppercase(),
        "trCrcyCd" to currency.uppercase(),
        "ctxAreaFk200" to pageCursor.foreignKeyContext,
        "ctxAreaNk200" to pageCursor.nextKeyContext,
    )
}

private fun BrokerAccountSnapshotQuery.toKisDomesticBalanceQuery(): Map<String, String> {
    return mapOf(
        "isMock" to isMock.toString(),
        "afhrFlprYn" to "N",
        "oflYn" to "",
        "inqrDvsn" to "02",
        "unprDvsn" to "01",
        "fundSttlIcldYn" to "N",
        "fncgAmtAutoRdptYn" to "N",
        "prcsDvsn" to "00",
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

private fun JsonNode.toBrokerAccountSnapshot(query: BrokerAccountSnapshotQuery): BrokerAccountSnapshot {
    val rows = rows("output1", "OUTPUT1", "output", "OUTPUT")
    val summary = nodeOrNull("output2", "OUTPUT2")
    val orderableCashAmount = summary?.textOrNull(
        "ovrs_ord_psbl_amt",
        "OVRS_ORD_PSBL_AMT",
        "frcr_ord_psbl_amt",
        "FRCR_ORD_PSBL_AMT",
        "ord_psbl_frcr_amt",
        "ORD_PSBL_FRCR_AMT",
        "buy_psbl_amt",
        "BUY_PSBL_AMT",
    ).toDoubleValue()
    val settledCashAmount = summary?.textOrNull(
        "frcr_dncl_amt_2",
        "FRCR_DNCL_AMT_2",
        "frcr_dncl_amt",
        "FRCR_DNCL_AMT",
    ).toDoubleValue()
    val withdrawableCashAmount = summary?.textOrNull(
        "frcr_wdrw_psbl_amt",
        "FRCR_WDRW_PSBL_AMT",
        "ovrs_wdrw_psbl_amt",
        "OVRS_WDRW_PSBL_AMT",
        "wdrw_psbl_amt",
        "WDRW_PSBL_AMT",
    ).toDoubleValue()

    return BrokerAccountSnapshot(
        market = StockOrderMarket.OVERSEAS_US,
        exchange = query.exchange.uppercase(),
        currency = query.currency.uppercase(),
        positions = rows.mapNotNull { it.toBrokerPositionSnapshot() },
        availableCashAmount = orderableCashAmount ?: settledCashAmount ?: withdrawableCashAmount,
        cashCurrency = query.currency.uppercase(),
        orderableCashAmount = orderableCashAmount,
        settledCashAmount = settledCashAmount,
        withdrawableCashAmount = withdrawableCashAmount,
        totalPurchaseAmount = summary?.textOrNull(
            "frcr_buy_amt_smtl",
            "FRCR_BUY_AMT_SMTL",
            "tot_pchs_amt",
            "TOT_PCHS_AMT",
            "pchs_amt_smtl",
            "PCHS_AMT_SMTL",
        ).toDoubleValue(),
        totalEvaluationAmount = summary?.textOrNull(
            "tot_evlu_amt",
            "TOT_EVLU_AMT",
            "frcr_evlu_amt2",
            "FRCR_EVLU_AMT2",
            "ovrs_stck_evlu_amt",
            "OVRS_STCK_EVLU_AMT",
        ).toDoubleValue(),
        totalProfitLossAmount = summary?.textOrNull(
            "tot_evlu_pfls_amt",
            "TOT_EVLU_PFLS_AMT",
            "evlu_pfls_amt_smtl",
            "EVLU_PFLS_AMT_SMTL",
            "frcr_evlu_pfls_amt",
            "FRCR_EVLU_PFLS_AMT",
        ).toDoubleValue(),
    )
}

private fun JsonNode.toDomesticBrokerAccountSnapshot(): BrokerAccountSnapshot {
    val rows = rows("output1", "OUTPUT1", "output", "OUTPUT")
    val summary = nodeOrNull("output2", "OUTPUT2")
    val orderableCashAmount = summary?.textOrNull(
        "ord_psbl_cash",
        "ORD_PSBL_CASH",
    ).toDoubleValue()
    val settledCashAmount = summary?.textOrNull(
        "dnca_tot_amt",
        "DNCA_TOT_AMT",
    ).toDoubleValue()
    val withdrawableCashAmount = summary?.textOrNull(
        "nxdy_excc_amt",
        "NXDY_EXCC_AMT",
        "prvs_rcdl_excc_amt",
        "PRVS_RCDL_EXCC_AMT",
        "wdrw_psbl_amt",
        "WDRW_PSBL_AMT",
    ).toDoubleValue()

    return BrokerAccountSnapshot(
        market = StockOrderMarket.DOMESTIC,
        exchange = DOMESTIC_EXCHANGE,
        currency = DOMESTIC_CURRENCY,
        positions = rows.mapNotNull { it.toBrokerPositionSnapshot() },
        availableCashAmount = orderableCashAmount ?: settledCashAmount ?: withdrawableCashAmount,
        cashCurrency = DOMESTIC_CURRENCY,
        orderableCashAmount = orderableCashAmount,
        settledCashAmount = settledCashAmount,
        withdrawableCashAmount = withdrawableCashAmount,
        totalPurchaseAmount = summary?.textOrNull(
            "pchs_amt_smtl",
            "PCHS_AMT_SMTL",
            "tot_pchs_amt",
            "TOT_PCHS_AMT",
        ).toDoubleValue(),
        totalEvaluationAmount = summary?.textOrNull(
            "tot_evlu_amt",
            "TOT_EVLU_AMT",
            "scts_evlu_amt",
            "SCTS_EVLU_AMT",
            "evlu_amt_smtl",
            "EVLU_AMT_SMTL",
            "nass_amt",
            "NASS_AMT",
        ).toDoubleValue(),
        totalProfitLossAmount = summary?.textOrNull(
            "evlu_pfls_smtl",
            "EVLU_PFLS_SMTL",
            "tot_evlu_pfls_amt",
            "TOT_EVLU_PFLS_AMT",
        ).toDoubleValue(),
    )
}

private fun JsonNode.rows(vararg fieldNames: String): List<JsonNode> {
    val node = nodeOrNull(*fieldNames) ?: return emptyList()
    return when {
        node.isArray -> node.toList()
        node.isObject -> listOf(node)
        else -> emptyList()
    }
}

private fun JsonNode.nodeOrNull(vararg fieldNames: String): JsonNode? {
    return fieldNames
        .asSequence()
        .map { path(it) }
        .firstOrNull { !it.isMissingNode && !it.isNull }
}

private fun BrokerAccountSnapshot.mergeSummaryFrom(next: BrokerAccountSnapshot): BrokerAccountSnapshot {
    return copy(
        market = next.market,
        exchange = next.exchange,
        currency = next.currency,
        positions = emptyList(),
        availableCashAmount = next.availableCashAmount ?: availableCashAmount,
        cashCurrency = next.cashCurrency ?: cashCurrency,
        orderableCashAmount = next.orderableCashAmount ?: orderableCashAmount,
        settledCashAmount = next.settledCashAmount ?: settledCashAmount,
        withdrawableCashAmount = next.withdrawableCashAmount ?: withdrawableCashAmount,
        totalPurchaseAmount = next.totalPurchaseAmount ?: totalPurchaseAmount,
        totalEvaluationAmount = next.totalEvaluationAmount ?: totalEvaluationAmount,
        totalProfitLossAmount = next.totalProfitLossAmount ?: totalProfitLossAmount,
    )
}

private fun JsonNode.toBrokerPositionSnapshot(): BrokerPositionSnapshot? {
    val symbol = textOrNull("ovrs_pdno", "OVRS_PDNO", "pdno", "PDNO") ?: return null
    val quantity = textOrNull("ovrs_cblc_qty", "OVRS_CBLC_QTY", "hldg_qty", "HLDG_QTY", "cblc_qty", "CBLC_QTY")
        .toLongValue()
    if (quantity <= 0) return null
    return BrokerPositionSnapshot(
        symbol = symbol,
        stockName = textOrNull("ovrs_item_name", "OVRS_ITEM_NAME", "prdt_name", "PRDT_NAME").orEmpty(),
        quantity = quantity,
        averagePurchasePrice = textOrNull("pchs_avg_pric", "PCHS_AVG_PRIC", "avg_prvs", "AVG_PRVS")
            .toDoubleValue(),
        currentPrice = textOrNull("now_pric2", "NOW_PRIC2", "ovrs_now_pric1", "OVRS_NOW_PRIC1", "prpr", "PRPR")
            .toDoubleValue(),
        purchaseAmount = textOrNull("frcr_pchs_amt1", "FRCR_PCHS_AMT1", "pchs_amt", "PCHS_AMT")
            .toDoubleValue(),
        evaluationAmount = textOrNull(
            "ovrs_stck_evlu_amt",
            "OVRS_STCK_EVLU_AMT",
            "frcr_evlu_amt2",
            "FRCR_EVLU_AMT2",
            "evlu_amt",
            "EVLU_AMT",
        ).toDoubleValue(),
        profitLossAmount = textOrNull(
            "frcr_evlu_pfls_amt",
            "FRCR_EVLU_PFLS_AMT",
            "evlu_pfls_amt",
            "EVLU_PFLS_AMT",
        ).toDoubleValue(),
    )
}

private fun DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1.toBrokerHistoryItem(): BrokerOrderHistoryItem? {
    val orderId = odno.trim()
    if (orderId.isBlank()) return null
    return BrokerOrderHistoryItem(
        externalOrderId = orderId,
        originalOrderId = orgnOdno.takeIf { it.isNotBlank() },
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
    val orderId = textOrNull("odno", "ODNO", "ord_no", "ORD_NO", "order_no", "ORDER_NO") ?: ""
    if (orderId.isBlank()) return null
    val orderedQuantity = longValue("ft_ord_qty", "FT_ORD_QTY", "ord_qty", "ORD_QTY")
    val filledQuantity = longValue("ft_ccld_qty", "FT_CCLD_QTY", "tot_ccld_qty", "TOT_CCLD_QTY", "ccld_qty")
    val remainingQuantity = longValue("nccs_qty", "NCCS_QTY", "rmn_qty", "RMN_QTY")
    val statusName = textOrNull("prcs_stat_name", "PRCS_STAT_NAME", "ord_stat_name", "ORD_STAT_NAME")
    val revisionCancelCode = textOrNull(
        "rvse_cncl_dvsn",
        "RVSE_CNCL_DVSN",
        "rvse_cncl_dvsn_cd",
        "RVSE_CNCL_DVSN_CD",
    )
    val revisionCancelName = textOrNull(
        "rvse_cncl_dvsn_name",
        "RVSE_CNCL_DVSN_NAME",
        "cncl_dvsn_name",
        "CNCL_DVSN_NAME",
    )
    val statusMessage = joinedText(statusName, revisionCancelName)
    val rejectionReason = joinedText(
        textOrNull("rjct_rson", "RJCT_RSON"),
        textOrNull("rjct_rson_name", "RJCT_RSON_NAME"),
        textOrNull("rjct_rson_cn", "RJCT_RSON_CN"),
        textOrNull("rjct_rson_cd_name", "RJCT_RSON_CD_NAME"),
    )
    val cancelled = statusMessage?.contains(CANCELLED_KOREAN) == true || revisionCancelCode == KIS_CANCEL_REVISION_CODE
    val explicitCancelledQuantity = longValue("cncl_cfrm_qty", "CNCL_CFRM_QTY", "cncl_qty", "CNCL_QTY")
    return BrokerOrderHistoryItem(
        externalOrderId = orderId,
        originalOrderId = textOrNull("orgn_odno", "ORGN_ODNO"),
        branchOrderNumber = textOrNull(
            "ord_gno_brno",
            "ORD_GNO_BRNO",
            "krx_fwdg_ord_orgno",
            "KRX_FWDG_ORD_ORGNO",
            "krxFwdgOrdOrgno",
        ),
        symbol = textOrNull("pdno", "PDNO", "ovrs_pdno", "OVRS_PDNO").orEmpty(),
        stockName = textOrNull("prdt_name", "PRDT_NAME", "prdt_eng_name", "PRDT_ENG_NAME").orEmpty(),
        orderedAt = parseKisOrderDateTime(
            textOrNull("ord_dt", "ORD_DT", "dmst_ord_dt", "DMST_ORD_DT").orEmpty(),
            textOrNull("ord_tmd", "ORD_TMD", "thco_ord_tmd", "THCO_ORD_TMD").orEmpty(),
        ),
        orderedQuantity = orderedQuantity,
        cumulativeFilledQuantity = filledQuantity,
        remainingQuantity = remainingQuantity,
        rejectedQuantity = if (rejectionReason != null) orderedQuantity else 0,
        cancelledQuantity = explicitCancelledQuantity.takeIf { it > 0 } ?: if (cancelled) remainingQuantity else 0,
        cancelled = cancelled,
        side = textOrNull(
            "sll_buy_dvsn_cd",
            "SLL_BUY_DVSN_CD",
            "sll_buy_dvsn_cd_name",
            "SLL_BUY_DVSN_CD_NAME",
            "sll_buy_dvsn_name",
            "SLL_BUY_DVSN_NAME",
        )
            .toOrderIntentSide(),
        averageExecutionPrice = textOrNull(
            "ft_ccld_unpr3",
            "FT_CCLD_UNPR3",
            "avg_prvs",
            "AVG_PRVS",
            "avg_ccld_pric",
            "AVG_CCLD_PRIC",
        ).toDoubleValue(),
        statusMessage = statusMessage,
        rejectionReason = rejectionReason,
    )
}

private fun JsonNode.textOrNull(vararg fieldNames: String): String? {
    return fieldNames
        .asSequence()
        .map { path(it).asText("").trim() }
        .firstOrNull { it.isNotBlank() }
}

private fun JsonNode.longValue(vararg fieldNames: String): Long {
    return textOrNull(*fieldNames).toLongValue()
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
            brokerMessage = order.msg1.takeIf { it.isNotBlank() },
        )
    }

    val externalOrderId = order.output.getODNO().takeIf { it.isNotBlank() }
        ?: throw BrokerOrderSubmissionUnknownException(
            message = "stock order accepted but broker order id is missing",
        )
    return BrokerOrderSubmissionDto(
        externalOrderId = externalOrderId,
        branchOrderNumber = order.output.getKRXFWDGORDORGNO().takeIf { it.isNotBlank() },
    )
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
    return when (this?.trim()?.uppercase()) {
        "01" -> OrderIntentSide.SELL
        "02" -> OrderIntentSide.BUY
        "SELL", "SELLING", "S", "\uB9E4\uB3C4" -> OrderIntentSide.SELL
        "BUY", "PURCHASE", "B", "\uB9E4\uC218" -> OrderIntentSide.BUY
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

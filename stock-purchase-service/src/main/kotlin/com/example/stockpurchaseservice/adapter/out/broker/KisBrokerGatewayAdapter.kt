package com.example.stockpurchaseservice.adapter.out.broker

import ApiResponse
import DailyExecutionOrdersResponseOuterClass
import com.example.common.ExternalApiAdapter
import com.example.common.endpoint.Endpoint.GET_EXECUTION_ORDERS
import com.example.common.endpoint.Endpoint.GET_OVERSEAS_EXECUTION_ORDERS
import com.example.common.endpoint.Endpoint.GET_OVERSEAS_STOCK_BALANCE
import com.example.common.endpoint.Endpoint.GET_OVERSEAS_STOCK_ORDER_UNFILLED
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
                val resolvedCommand = prepareDomesticCancelCommand(command)
                guard.execute("cancel-order:${command.market}") {
                    stockApiClient.submitStockOrder(
                        uri = OPEN_API_PREFIX + POST_STOCK_ORDER_CANCEL,
                        body = resolvedCommand.toKisDomesticCancelRequest(),
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

    private fun prepareDomesticCancelCommand(command: BrokerOrderCancelCommand): BrokerOrderCancelCommand {
        val cancelableOrder = findCancelableOrders(command.toCancelableQuery())
            .bestCancelableOrder()
            ?: throw BrokerOrderRejectedException(
                message = "domestic order is not cancelable: ${command.originalOrderId}",
            )
        if (cancelableOrder.possibleQuantity < command.quantity) {
            throw BrokerOrderRejectedException(
                message = "domestic order cancel quantity exceeds possible quantity: " +
                    "requested=${command.quantity} possible=${cancelableOrder.possibleQuantity}",
            )
        }
        val branchOrderNumber = command.branchOrderNumber
            ?.takeIf { it.isNotBlank() }
            ?: cancelableOrder.branchOrderNumber?.takeIf { it.isNotBlank() }
            ?: throw BrokerOrderRejectedException(
                message = "domestic cancelable order has no branch order number: ${command.originalOrderId}",
            )
        return command.copy(branchOrderNumber = branchOrderNumber)
    }

    override fun findCancelableOrders(query: BrokerOrderCancelableQuery): List<BrokerCancelableOrderItem> {
        val items = mutableListOf<BrokerCancelableOrderItem>()
        var cursor = query.pageCursor
        val seenCursors = mutableSetOf<BrokerOrderHistoryPageCursor>()
        var pageCount = 0
        do {
            if (!seenCursors.add(cursor)) break
            val page = when (query.market) {
                StockOrderMarket.DOMESTIC -> fetchDomesticCancelableOrderPage(query.copy(pageCursor = cursor))
                StockOrderMarket.OVERSEAS_US -> fetchOverseasCancelableOrderPage(query.copy(pageCursor = cursor))
            }
            items += page.items
            cursor = page.nextCursor
            pageCount += 1
        } while (cursor.hasNext() && pageCount < MAX_CANCELABLE_ORDER_PAGES)

        return items.filter { it.matches(query) }
    }

    private fun fetchDomesticCancelableOrderPage(query: BrokerOrderCancelableQuery): KisCancelableOrderPage {
        return guard.executeQuery("domestic-cancelable-order") {
            stockApiClient.fetchDomesticCancelableOrderPage(query, properties.toQueryCallOptions())
        }
    }

    private fun fetchOverseasCancelableOrderPage(query: BrokerOrderCancelableQuery): KisCancelableOrderPage {
        return guard.executeQuery("overseas-unfilled-order") {
            stockApiClient.fetchOverseasCancelableOrderPage(query, properties.toQueryCallOptions())
        }
    }

    private fun BrokerOrderCancelCommand.toCancelableQuery(): BrokerOrderCancelableQuery {
        return BrokerOrderCancelableQuery(
            market = market,
            symbol = symbol,
            exchange = exchange,
            originalOrderId = originalOrderId,
            branchOrderNumber = branchOrderNumber,
            isMock = isMock,
        )
    }

    private fun ensureOverseasOrderCancelable(command: BrokerOrderCancelCommand) {
        val cancelableOrder = findCancelableOrders(command.toCancelableQuery())
            .bestCancelableOrder()
            ?: throw BrokerOrderRejectedException(
                message = "overseas order is not cancelable: ${command.originalOrderId}",
            )

        if (cancelableOrder.possibleQuantity <= 0) {
            throw BrokerOrderRejectedException(
                message = "overseas order has no cancelable quantity: ${command.originalOrderId}",
            )
        }
        if (cancelableOrder.possibleQuantity < command.quantity) {
            throw BrokerOrderRejectedException(
                message = "overseas order cancel quantity exceeds remaining quantity: " +
                    "requested=${command.quantity} remaining=${cancelableOrder.possibleQuantity}",
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
            val returnCode = response.kisReturnCode()
            if (returnCode.isNotBlank() && returnCode != "0") {
                throwKisBrokerBusinessFailure(
                    operation = "overseas execution lookup",
                    returnCode = returnCode,
                    messageCode = response.kisMessageCode(),
                    brokerMessage = response.kisMessage(),
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
            val returnCode = response.kisReturnCode()
            if (returnCode.isNotBlank() && returnCode != "0") {
                throwKisBrokerBusinessFailure(
                    operation = "domestic balance lookup",
                    returnCode = returnCode,
                    messageCode = response.kisMessageCode(),
                    brokerMessage = response.kisMessage(),
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
            val returnCode = response.kisReturnCode()
            if (returnCode.isNotBlank() && returnCode != "0") {
                throwKisBrokerBusinessFailure(
                    operation = "overseas balance lookup",
                    returnCode = returnCode,
                    messageCode = response.kisMessageCode(),
                    brokerMessage = response.kisMessage(),
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
        "OVRS_EXCG_CD" to exchange.toKisUsExchangeCode(),
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
        "OVRS_EXCG_CD" to exchange.toKisUsExchangeCode(),
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

private fun BrokerOrderCancelableQuery.toKisDomesticCancelableOrderQuery(): Map<String, String> {
    return mapOf(
        "isMock" to isMock.toString(),
        "inqrDvsn1" to "1",
        "inqrDvsn2" to "0",
        "ctxAreaFk100" to pageCursor.foreignKeyContext,
        "ctxAreaNk100" to pageCursor.nextKeyContext,
    )
}

private fun List<BrokerCancelableOrderItem>.bestCancelableOrder(): BrokerCancelableOrderItem? {
    return maxByOrNull { it.possibleQuantity }
}

private fun WebClient.fetchDomesticCancelableOrderPage(
    query: BrokerOrderCancelableQuery,
    callOptions: ExternalApiCallOptions,
): KisCancelableOrderPage {
    val response = getExternalApi(
        uri = OPEN_API_PREFIX + GET_STOCK_ORDER_CANCELABLE,
        queryParameters = query.toKisDomesticCancelableOrderQuery(),
        responseType = JsonNode::class.java,
        callOptions = callOptions,
    )
    val returnCode = response.kisReturnCode()
    if (returnCode.isNotBlank() && returnCode != "0") {
        throwKisBrokerBusinessFailure(
            operation = "domestic cancelable order lookup",
            returnCode = returnCode,
            messageCode = response.kisMessageCode(),
            brokerMessage = response.kisMessage(),
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

private fun BrokerOrderCancelableQuery.toKisOverseasCancelableOrderQuery(): Map<String, String> {
    return mapOf(
        "isMock" to isMock.toString(),
        "ovrsExcgCd" to exchange.uppercase(),
        "sortSqn" to "DS",
        "ctxAreaFk200" to pageCursor.foreignKeyContext,
        "ctxAreaNk200" to pageCursor.nextKeyContext,
    )
}

private fun WebClient.fetchOverseasCancelableOrderPage(
    query: BrokerOrderCancelableQuery,
    callOptions: ExternalApiCallOptions,
): KisCancelableOrderPage {
    val response = getExternalApi(
        uri = OPEN_API_PREFIX + GET_OVERSEAS_STOCK_ORDER_UNFILLED,
        queryParameters = query.toKisOverseasCancelableOrderQuery(),
        responseType = JsonNode::class.java,
        callOptions = callOptions,
    )
    val returnCode = response.kisReturnCode()
    if (returnCode.isNotBlank() && returnCode != "0") {
        throwKisBrokerBusinessFailure(
            operation = "overseas unfilled order lookup",
            returnCode = returnCode,
            messageCode = response.kisMessageCode(),
            brokerMessage = response.kisMessage(),
        )
    }
    val rows = response.rows("output", "OUTPUT", "output1", "OUTPUT1")
    return KisCancelableOrderPage(
        items = rows.mapNotNull { it.toKisCancelableOrderItem() },
        nextCursor = BrokerOrderHistoryPageCursor(
            foreignKeyContext = response.textOrNull("ctx_area_fk200", "ctxAreaFk200", "CTX_AREA_FK200").orEmpty(),
            nextKeyContext = response.textOrNull("ctx_area_nk200", "ctxAreaNk200", "CTX_AREA_NK200").orEmpty(),
        ),
    )
}

private data class KisCancelableOrderPage(
    val items: List<BrokerCancelableOrderItem>,
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

private fun JsonNode.toKisCancelableOrderItem(): BrokerCancelableOrderItem? {
    val orderId = textOrNull("odno", "ODNO", "ordNo", "ord_no", "ORD_NO", "orderNo", "order_no", "ORDER_NO")
        .toBrokerOrderIdOrNull()
        .orEmpty()
    val originalOrderId = textOrNull("orgn_odno", "ORGN_ODNO", "orgnOdno").toBrokerOrderIdOrNull()
    val matchableOrderId = orderId.ifBlank { originalOrderId.orEmpty() }
    if (matchableOrderId.isBlank()) return null
    return BrokerCancelableOrderItem(
        branchOrderNumber = textOrNull(
            "ord_gno_brno",
            "ordGnoBrno",
            "ORD_GNO_BRNO",
            "krx_fwdg_ord_orgno",
            "KRX_FWDG_ORD_ORGNO",
            "krxFwdgOrdOrgno",
            "ord_orgno",
            "ORD_ORGNO",
            "ordOrgno",
        ).toBrokerBranchOrderNumberOrNull(),
        orderId = orderId,
        originalOrderId = originalOrderId,
        symbol = textOrNull(
            "pdno",
            "PDNO",
            "ovrs_pdno",
            "ovrsPdno",
            "OVRS_PDNO",
            "prdt_code",
            "prdtCode",
            "PRDT_CODE",
        ).orEmpty(),
        possibleQuantity = longValue(
            "psbl_qty",
            "psblQty",
            "PSBL_QTY",
            "nccs_qty",
            "nccsQty",
            "NCCS_QTY",
            "rmn_qty",
            "rmnQty",
            "RMN_QTY",
            "ord_psbl_qty",
            "ordPsblQty",
            "ORD_PSBL_QTY",
            "rvse_cncl_psbl_qty",
            "rvseCnclPsblQty",
            "RVSE_CNCL_PSBL_QTY",
            "psbl_rvse_cncl_qty",
            "psblRvseCnclQty",
            "PSBL_RVSE_CNCL_QTY",
        ),
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
        "ordGnoBrno" to branchOrderNumber.orEmpty(),
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
        "ovrsExcgCd" to if (isMock) "" else exchange.uppercase(),
        "sortSqn" to "DS",
        "ordDt" to "",
        "ordGnoBrno" to branchOrderNumber.orEmpty(),
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
    val summary = summaryNode("output2", "OUTPUT2")
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
    val summary = summaryNode("output2", "OUTPUT2")
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

private fun JsonNode.summaryNode(vararg fieldNames: String): JsonNode? {
    val node = nodeOrNull(*fieldNames) ?: return null
    return when {
        node.isObject -> node
        node.isArray -> node.firstOrNull { it.isObject }
        else -> null
    }
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
    val orderId = odno.toBrokerOrderIdOrNull() ?: return null
    return BrokerOrderHistoryItem(
        externalOrderId = orderId,
        externalExecutionId = ccldNo.toBrokerExecutionIdOrNull(),
        originalOrderId = orgnOdno.toBrokerOrderIdOrNull(),
        branchOrderNumber = ordGnoBrno.toBrokerBranchOrderNumberOrNull()
            ?: ordOrgno.toBrokerBranchOrderNumberOrNull(),
        symbol = pdno.trim(),
        stockName = prdtName.trim(),
        orderedAt = parseKisOrderDateTime(ordDt, ordTmd),
        orderedQuantity = ordQty.toLongValue(),
        orderedPrice = ordUnpr.toDoubleValue(),
        cumulativeFilledQuantity = totCcldQty.toLongValue(),
        remainingQuantity = rmnQty.toLongValue(),
        rejectedQuantity = rjctQty.toLongValue(),
        cancelledQuantity = cnclCfrmQty.toLongValue(),
        cancelled = cnclYn.equals("Y", ignoreCase = true),
        side = sllBuyDvsnCd.toOrderIntentSide() ?: sllBuyDvsnCdName.toOrderIntentSide(),
        averageExecutionPrice = avgPrvs.toDoubleValue(),
        statusMessage = joinedText(
            ccldCndtName,
            prcsStatName,
            ordStatName,
            rvseCnclDvsnName,
        ),
        rejectionReason = joinedRejectionReason(
            rjctRson,
            rjctRsonName,
            rjctRsonCn,
            rjctRsonCd,
            rjctRsonCdName,
        ),
    )
}

private fun JsonNode.toBrokerHistoryItem(): BrokerOrderHistoryItem? {
    val orderId = textOrNull(
        "odno",
        "ODNO",
        "ordNo",
        "ord_no",
        "ORD_NO",
        "orderNo",
        "order_no",
        "ORDER_NO",
    ).toBrokerOrderIdOrNull() ?: return null
    val orderedQuantity = longValue(
        "ft_ord_qty",
        "ftOrdQty",
        "FT_ORD_QTY",
        "ovrs_ord_qty",
        "ovrsOrdQty",
        "OVRS_ORD_QTY",
        "ord_qty",
        "ordQty",
        "ORD_QTY",
    )
    val orderedPrice = textOrNull(
        "ft_ord_unpr",
        "ftOrdUnpr",
        "FT_ORD_UNPR",
        "ft_ord_unpr3",
        "ftOrdUnpr3",
        "FT_ORD_UNPR3",
        "ord_unpr",
        "ordUnpr",
        "ORD_UNPR",
        "ord_unpr3",
        "ordUnpr3",
        "ORD_UNPR3",
        "ovrs_ord_unpr",
        "ovrsOrdUnpr",
        "OVRS_ORD_UNPR",
        "ovrs_ord_unpr3",
        "ovrsOrdUnpr3",
        "OVRS_ORD_UNPR3",
    ).toDoubleValue()
    val filledQuantity = longValue(
        "ft_ccld_qty",
        "ftCcldQty",
        "FT_CCLD_QTY",
        "ovrs_ccld_qty",
        "ovrsCcldQty",
        "OVRS_CCLD_QTY",
        "tot_ccld_qty",
        "totCcldQty",
        "TOT_CCLD_QTY",
        "ccld_qty",
        "ccldQty",
        "CCLD_QTY",
    )
    val remainingQuantity = longValue(
        "nccs_qty",
        "nccsQty",
        "NCCS_QTY",
        "ovrs_nccs_qty",
        "ovrsNccsQty",
        "OVRS_NCCS_QTY",
        "rmn_qty",
        "rmnQty",
        "RMN_QTY",
    )
    val statusName = textOrNull(
        "prcs_stat_name",
        "prcsStatName",
        "PRCS_STAT_NAME",
        "ord_stat_name",
        "ordStatName",
        "ORD_STAT_NAME",
    )
    val revisionCancelCode = textOrNull(
        "rvse_cncl_dvsn",
        "rvseCnclDvsn",
        "RVSE_CNCL_DVSN",
        "rvse_cncl_dvsn_cd",
        "rvseCnclDvsnCd",
        "RVSE_CNCL_DVSN_CD",
    )
    val revisionCancelName = textOrNull(
        "rvse_cncl_dvsn_name",
        "rvseCnclDvsnName",
        "RVSE_CNCL_DVSN_NAME",
        "cncl_dvsn_name",
        "cnclDvsnName",
        "CNCL_DVSN_NAME",
    )
    val statusMessage = joinedText(statusName, revisionCancelName)
    val rejectionReason = joinedRejectionReason(
        textOrNull("rjct_rson", "rjctRson", "RJCT_RSON"),
        textOrNull("rjct_rson_name", "rjctRsonName", "RJCT_RSON_NAME"),
        textOrNull("rjct_rson_cn", "rjctRsonCn", "RJCT_RSON_CN"),
        textOrNull("rjct_rson_cd", "rjctRsonCd", "RJCT_RSON_CD"),
        textOrNull("rjct_rson_cd_name", "rjctRsonCdName", "RJCT_RSON_CD_NAME"),
    )
    val cancelled = statusMessage?.contains(CANCELLED_KOREAN) == true ||
        revisionCancelCode == KIS_CANCEL_REVISION_CODE ||
        textOrNull("cncl_yn", "cnclYn", "CNCL_YN").equals("Y", ignoreCase = true)
    val explicitCancelledQuantity = longValue(
        "cncl_cfrm_qty",
        "cnclCfrmQty",
        "CNCL_CFRM_QTY",
        "cncl_qty",
        "cnclQty",
        "CNCL_QTY",
    )
    val explicitRejectedQuantity = longValue(
        "rjct_qty",
        "rjctQty",
        "RJCT_QTY",
        "rjct_cfrm_qty",
        "rjctCfrmQty",
        "RJCT_CFRM_QTY",
    )
    return BrokerOrderHistoryItem(
        externalOrderId = orderId,
        externalExecutionId = textOrNull(
            "ccld_no",
            "ccldNo",
            "CCLD_NO",
            "ft_ccld_no",
            "ftCcldNo",
            "FT_CCLD_NO",
            "ovrs_ccld_no",
            "ovrsCcldNo",
            "OVRS_CCLD_NO",
            "exec_no",
            "execNo",
            "EXEC_NO",
            "execution_no",
            "executionNo",
            "EXECUTION_NO",
            "cntr_no",
            "cntrNo",
            "CNTR_NO",
        ).toBrokerExecutionIdOrNull(),
        originalOrderId = textOrNull("orgn_odno", "orgnOdno", "ORGN_ODNO").toBrokerOrderIdOrNull(),
        branchOrderNumber = textOrNull(
            "ord_gno_brno",
            "ordGnoBrno",
            "ORD_GNO_BRNO",
            "krx_fwdg_ord_orgno",
            "KRX_FWDG_ORD_ORGNO",
            "krxFwdgOrdOrgno",
            "ord_orgno",
            "ORD_ORGNO",
            "ordOrgno",
        ).toBrokerBranchOrderNumberOrNull(),
        symbol = textOrNull("pdno", "PDNO", "ovrs_pdno", "ovrsPdno", "OVRS_PDNO").orEmpty(),
        stockName = textOrNull(
            "prdt_name",
            "prdtName",
            "PRDT_NAME",
            "prdt_eng_name",
            "prdtEngName",
            "PRDT_ENG_NAME",
        ).orEmpty(),
        orderedAt = parseKisOrderDateTime(
            textOrNull("ord_dt", "ordDt", "ORD_DT", "dmst_ord_dt", "dmstOrdDt", "DMST_ORD_DT").orEmpty(),
            textOrNull("ord_tmd", "ordTmd", "ORD_TMD", "thco_ord_tmd", "thcoOrdTmd", "THCO_ORD_TMD").orEmpty(),
        ),
        orderedQuantity = orderedQuantity,
        orderedPrice = orderedPrice,
        cumulativeFilledQuantity = filledQuantity,
        remainingQuantity = remainingQuantity,
        rejectedQuantity = explicitRejectedQuantity.takeIf { it > 0 } ?: if (rejectionReason != null) orderedQuantity else 0,
        cancelledQuantity = explicitCancelledQuantity.takeIf { it > 0 } ?: if (cancelled) remainingQuantity else 0,
        cancelled = cancelled,
        side = textOrNull(
            "sll_buy_dvsn_cd",
            "sllBuyDvsnCd",
            "SLL_BUY_DVSN_CD",
            "sll_buy_dvsn_cd_name",
            "sllBuyDvsnCdName",
            "SLL_BUY_DVSN_CD_NAME",
            "sll_buy_dvsn_name",
            "sllBuyDvsnName",
            "SLL_BUY_DVSN_NAME",
        )
            .toOrderIntentSide(),
        averageExecutionPrice = textOrNull(
            "ft_ccld_unpr",
            "ftCcldUnpr",
            "FT_CCLD_UNPR",
            "ft_ccld_unpr3",
            "ftCcldUnpr3",
            "FT_CCLD_UNPR3",
            "ccld_unpr",
            "ccldUnpr",
            "CCLD_UNPR",
            "ovrs_ccld_unpr",
            "ovrsCcldUnpr",
            "OVRS_CCLD_UNPR",
            "avg_prvs",
            "avgPrvs",
            "AVG_PRVS",
            "avg_ccld_pric",
            "avgCcldPric",
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

private fun JsonNode.kisReturnCode(): String {
    return textOrNull("rt_cd", "rtCd", "rtCode", "RT_CD", "RT_CODE").orEmpty()
}

private fun JsonNode.kisMessageCode(): String? {
    return textOrNull("msg_cd", "msgCd", "msgCode", "MSG_CD", "MSG_CODE")
}

private fun JsonNode.kisMessage(): String? {
    return textOrNull("msg1", "msg_1", "msg", "message", "MSG1", "MSG")
}

private fun JsonNode.longValue(vararg fieldNames: String): Long {
    return textOrNull(*fieldNames).toLongValue()
}

private fun JsonNode.textOrNull(): String? {
    return asText("").trim().takeIf { it.isNotBlank() }
}

private fun String?.toBrokerOrderIdOrNull(): String? {
    val value = this?.trim()?.takeIf(String::isNotBlank) ?: return null
    return value.takeUnless { it.all { character -> character == '0' } }
}

private fun String?.toBrokerExecutionIdOrNull(): String? {
    return toBrokerOrderIdOrNull()
}

private fun String?.toBrokerBranchOrderNumberOrNull(): String? {
    return toBrokerOrderIdOrNull()
}

private fun joinedText(vararg values: String?): String? {
    return values
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .joinToString("; ")
        .takeIf(String::isNotBlank)
}

private fun joinedRejectionReason(vararg values: String?): String? {
    val tokens = values
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
    val hasNonRejectionMarker = tokens.any { it.isNonRejectionReasonMarker() }
    val rejectionTokens = tokens.filterNot { it.isNonRejectionReasonMarker() }
    return when {
        rejectionTokens.isEmpty() -> null
        hasNonRejectionMarker && rejectionTokens.all { it.isKisReasonCodeToken() } -> null
        else -> rejectionTokens.joinToString("; ")
    }
}

private fun String.isNonRejectionReasonMarker(): Boolean {
    val normalized = lowercase()
        .filterNot { it.isWhitespace() || it == '_' || it == '-' || it == '/' || it == '.' }
    return normalized.isBlank() || normalized in NON_REJECTION_REASON_MARKERS
}

private fun String.isKisReasonCodeToken(): Boolean {
    return KIS_REASON_CODE_TOKEN.matches(trim())
}

private val NON_REJECTION_REASON_MARKERS = setOf(
    "notrejected",
    "norejection",
    "none",
    "na",
    "notapplicable",
    "\uC5C6\uC74C",
    "\uD574\uB2F9\uC5C6\uC74C",
    "\uAC70\uBD80\uC0AC\uC720\uC5C6\uC74C",
    "\uAC70\uC808\uC0AC\uC720\uC5C6\uC74C",
)

private val KIS_REASON_CODE_TOKEN = Regex("[A-Za-z]{2,}[A-Za-z0-9_-]*\\d[A-Za-z0-9_-]*")

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

    val externalOrderId = order.output.getODNO().toBrokerOrderIdOrNull()
        ?: throw BrokerOrderSubmissionUnknownException(
            message = "stock order accepted but broker order id is missing",
        )
    return BrokerOrderSubmissionDto(
        externalOrderId = externalOrderId,
        branchOrderNumber = order.output.getKRXFWDGORDORGNO().toBrokerBranchOrderNumberOrNull(),
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
            StockOrderType.MOC -> "32"
        }

        OrderIntentSide.SELL -> when (this) {
            StockOrderType.LIMIT -> "00"
            StockOrderType.LOC -> "34"
            StockOrderType.MOC -> "33"
        }
    }
}

private fun String.toKisUsExchangeCode(): String {
    val exchange = trim().uppercase().ifBlank { DEFAULT_US_EXCHANGE }
    require(exchange in SUPPORTED_US_EXCHANGES) {
        "unsupported US overseas exchange: $exchange"
    }
    return exchange
}

private fun Double.toBrokerPrice(): String {
    return BigDecimal.valueOf(this)
        .stripTrailingZeros()
        .toPlainString()
}

private const val DEFAULT_US_EXCHANGE = "NASD"
private val SUPPORTED_US_EXCHANGES = setOf("NASD", "NYSE", "AMEX")

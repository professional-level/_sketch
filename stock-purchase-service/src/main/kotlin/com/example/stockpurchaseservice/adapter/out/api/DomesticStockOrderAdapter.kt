package com.example.stockpurchaseservice.adapter.out.api

import ApiResponse
import DailyExecutionOrdersResponseOuterClass
import com.example.common.endpoint.Endpoint.GET_EXECUTION_ORDERS
import com.example.common.ExternalApiAdapter
import com.example.common.endpoint.Endpoint.POST_STOCK_ORDER
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.DomesticStockOrderPort
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@ExternalApiAdapter
internal class DomesticStockOrderAdapter(
    @Qualifier("stockApiClient") private val stockApiClient: WebClient,
    @Value("\${akra.order.domestic.mock:true}") private val isMockOrder: Boolean,
) : DomesticStockOrderPort {

    override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
        val body = mapOf(
            "PDNO" to order.stockId,
            "ORD_DVSN" to order.orderType.toDomesticOrderDivision(),
            "ORD_QTY" to order.quantity,
            "ORD_UNPR" to order.purchasePrice.toLong(),
            "EXCG_ID_DVSN_CD" to "KRX",
            "SLL_TYPE" to "",
            "isMock" to isMockOrder,
        )

        return stockApiClient.submitStockOrder(
            uri = OPEN_API_PREFIX + POST_STOCK_ORDER,
            body = body,
            fallbackOrderId = order.orderId.toString(),
        )
    }

    override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
        val body = mapOf(
            "PDNO" to order.stockId,
            "ORD_DVSN" to order.orderType.toDomesticOrderDivision(),
            "ORD_QTY" to order.quantity,
            "ORD_UNPR" to order.sellingPrice.toLong(),
            "EXCG_ID_DVSN_CD" to "KRX",
            "SLL_TYPE" to "01",
            "isMock" to isMockOrder,
        )

        return stockApiClient.submitStockOrder(
            uri = OPEN_API_PREFIX + POST_STOCK_ORDER,
            body = body,
            fallbackOrderId = order.orderId.toString(),
        )
    }

    override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
        return fetchOrderHistoryRows().mapNotNull { it.toExecutionDto() }
    }

    override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
        return fetchOrderHistoryRows(
            symbol = query.symbol.takeIf { query.externalOrderId == null }.orEmpty(),
            externalOrderId = query.externalOrderId.orEmpty(),
            date = query.submittedAt ?: ZonedDateTime.now(KIS_DOMESTIC_ZONE),
        ).findStatusFor(query)
    }

    private fun StockOrderType.toDomesticOrderDivision(): String {
        return when (this) {
            StockOrderType.LIMIT,
            StockOrderType.LOC -> "00"

            StockOrderType.MOC -> "01"
        }
    }

    private fun fetchOrderHistoryRows(
        symbol: String = "",
        externalOrderId: String = "",
        date: ZonedDateTime = ZonedDateTime.now(KIS_DOMESTIC_ZONE),
    ): List<KisBrokerOrderHistoryRow> {
        val response = stockApiClient.getExternalApi(
            uri = OPEN_API_PREFIX + GET_EXECUTION_ORDERS,
            queryParameters = domesticExecutionOrderQuery(symbol, externalOrderId, date),
            responseType = DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersResponse::class.java,
            accept = MediaType.APPLICATION_PROTOBUF,
        )
        if (response.rtCd.isNotBlank() && response.rtCd != "0") {
            throw RuntimeException("domestic execution lookup failed: ${response.msgCd} ${response.msg1}".trim())
        }
        return response.output1List.mapNotNull { it.toHistoryRow() }
    }

    private fun domesticExecutionOrderQuery(
        symbol: String,
        externalOrderId: String,
        date: ZonedDateTime,
    ): Map<String, String> {
        val yyyymmdd = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        return mapOf(
            "isMock" to isMockOrder.toString(),
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

    private fun DailyExecutionOrdersResponseOuterClass.DailyExecutionOrdersOutput1.toHistoryRow(): KisBrokerOrderHistoryRow? {
        val orderId = odno.trim()
        if (orderId.isBlank()) return null
        return KisBrokerOrderHistoryRow(
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

    companion object {
        private val KIS_DOMESTIC_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
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

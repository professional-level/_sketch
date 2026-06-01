package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import java.time.ZonedDateTime
import java.util.UUID

interface MarketServicePort {
    fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto
    fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto
    fun findExecutionListAtOneDay(): List<ExecutedStockDto>
    fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto
}

interface DomesticStockOrderPort {
    fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto
    fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto
    fun findExecutionListAtOneDay(): List<ExecutedStockDto>
    fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto
}

interface OverseasStockOrderPort {
    fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto
    fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto
    fun findExecutionListAtOneDay(): List<ExecutedStockDto>
    fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto
}

data class BrokerOrderSubmissionDto(
    val externalOrderId: String,
)

class BrokerOrderSubmissionUnknownException(
    message: String,
    val externalOrderId: String? = null,
) : RuntimeException(message)

data class BrokerOrderStatusQuery(
    val orderIntentId: UUID,
    val internalOrderId: UUID,
    val externalOrderId: String?,
    val symbol: String,
    val side: OrderIntentSide,
    val market: StockOrderMarket,
    val submittedAt: ZonedDateTime? = null,
)

data class BrokerOrderStatusDto(
    val status: BrokerOrderStatus,
    val externalOrderId: String? = null,
    val reason: String? = null,
    val checkedAt: ZonedDateTime = ZonedDateTime.now(),
)

enum class BrokerOrderStatus {
    SUBMITTED,
    REJECTED,
    CANCELLED,
    UNKNOWN,
}

data class PurchaseOrderDto(
    val orderId: UUID,
    val stockId: String,
    val purchasePrice: Double,
    val quantity: Int,
    val market: StockOrderMarket = StockOrderMarket.DOMESTIC,
    val orderType: StockOrderType = StockOrderType.LIMIT,
)

data class SellingOrderDto(
    val orderId: UUID,
    val stockId: String,
    val sellingPrice: Double,
    val quantity: Int,
    val market: StockOrderMarket = StockOrderMarket.DOMESTIC,
    val orderType: StockOrderType = StockOrderType.LIMIT,
)

enum class StockOrderMarket {
    DOMESTIC,
    OVERSEAS_US,
}

enum class StockOrderType {
    LIMIT,
    LOC,
    MOC,
}

data class ExecutedStockDto(
    val stockId: String,
    val stockName: String,
    val createdAt: ZonedDateTime,
    val quantity: Int,
    val type: ExecutionTypeDto,
    val externalOrderId: String,
    val externalExecutionId: String,
    val averageExecutionPrice: Double? = null,
    val quantityMode: ExecutionQuantityModeDto = ExecutionQuantityModeDto.DELTA,
)

enum class ExecutionQuantityModeDto {
    DELTA,
    CUMULATIVE,
}

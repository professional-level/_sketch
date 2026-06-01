package com.example.stockpurchaseservice.application.port.out

import java.time.ZonedDateTime
import java.util.UUID

interface MarketServicePort {
    fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto
    fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto
    fun findExecutionListAtOneDay(): List<ExecutedStockDto>
}

interface DomesticStockOrderPort {
    fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto
    fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto
}

interface OverseasStockOrderPort {
    fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto
    fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto
}

data class BrokerOrderSubmissionDto(
    val externalOrderId: String,
)

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
)

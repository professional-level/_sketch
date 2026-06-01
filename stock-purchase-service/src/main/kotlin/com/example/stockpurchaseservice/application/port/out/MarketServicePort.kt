package com.example.stockpurchaseservice.application.port.out

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import java.time.ZonedDateTime
import java.util.UUID

interface MarketServicePort {
    fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto
    fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto
    fun cancelOrder(order: CancelOrderDto): BrokerOrderSubmissionDto {
        throw UnsupportedOperationException("cancel order is not supported")
    }
    fun findExecutionList(query: ExecutionLookupQuery): List<ExecutedStockDto> = findExecutionListAtOneDay()
    fun findExecutionListAtOneDay(): List<ExecutedStockDto>
    fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto
    fun findAccountSnapshot(query: AccountSnapshotQuery): AccountSnapshotDto {
        throw UnsupportedOperationException("account snapshot is not supported")
    }
}

interface DomesticStockOrderPort {
    fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto
    fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto
    fun cancelOrder(order: CancelOrderDto): BrokerOrderSubmissionDto
    fun findExecutionList(query: ExecutionLookupQuery): List<ExecutedStockDto> = findExecutionListAtOneDay()
    fun findExecutionListAtOneDay(): List<ExecutedStockDto>
    fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto
    fun findAccountSnapshot(query: AccountSnapshotQuery): AccountSnapshotDto {
        throw UnsupportedOperationException("domestic account snapshot is not supported")
    }
}

interface OverseasStockOrderPort {
    fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto
    fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto
    fun cancelOrder(order: CancelOrderDto): BrokerOrderSubmissionDto
    fun findExecutionList(query: ExecutionLookupQuery): List<ExecutedStockDto> = findExecutionListAtOneDay()
    fun findExecutionListAtOneDay(): List<ExecutedStockDto>
    fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto
    fun findAccountSnapshot(query: AccountSnapshotQuery): AccountSnapshotDto {
        throw UnsupportedOperationException("overseas account snapshot is not supported")
    }
}

data class BrokerOrderSubmissionDto(
    val externalOrderId: String,
)

class BrokerOrderSubmissionUnknownException(
    message: String,
    val externalOrderId: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class BrokerOrderRejectedException(
    message: String,
    val brokerReturnCode: String? = null,
    val brokerMessageCode: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class BrokerOrderTemporaryUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

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

data class CancelOrderDto(
    val orderId: UUID,
    val stockId: String,
    val originalOrderId: String,
    val branchOrderNumber: String? = null,
    val quantity: Int,
    val market: StockOrderMarket,
    val orderType: StockOrderType = StockOrderType.LIMIT,
    val price: Double = 0.0,
    val cancelAll: Boolean = true,
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

data class ExecutionLookupQuery(
    val from: ZonedDateTime,
    val to: ZonedDateTime,
) {
    init {
        require(!from.isAfter(to)) { "execution lookup from must be before or equal to to" }
    }
}

data class AccountSnapshotQuery(
    val market: StockOrderMarket,
    val exchange: String = "NASD",
    val currency: String = "USD",
)

data class AccountSnapshotDto(
    val market: StockOrderMarket,
    val exchange: String,
    val currency: String,
    val positions: List<AccountPositionSnapshotDto>,
    val availableCashAmount: Double? = null,
    val totalPurchaseAmount: Double? = null,
    val totalEvaluationAmount: Double? = null,
    val totalProfitLossAmount: Double? = null,
)

data class AccountPositionSnapshotDto(
    val symbol: String,
    val stockName: String,
    val quantity: Long,
    val averagePurchasePrice: Double? = null,
    val currentPrice: Double? = null,
    val purchaseAmount: Double? = null,
    val evaluationAmount: Double? = null,
    val profitLossAmount: Double? = null,
)

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

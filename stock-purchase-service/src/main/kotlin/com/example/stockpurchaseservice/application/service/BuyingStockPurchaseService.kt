package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.BuyingStockPurchaseUseCase
import com.example.stockpurchaseservice.application.port.out.ProcessedEventPort
import com.example.stockpurchaseservice.application.service.strategy.StockPurchaseHandlerFactory
import com.example.stockpurchaseservice.domain.Money
import com.example.stockpurchaseservice.domain.OrderId
import org.springframework.stereotype.Service
import java.time.ZonedDateTime
import java.util.UUID

@Service
class BuyingStockPurchaseService(
    private val handlerFactory: StockPurchaseHandlerFactory,
    private val processedEventPort: ProcessedEventPort,
) : BuyingStockPurchaseUseCase {

    override suspend fun execute(request: BuyingStockPurchaseCommand): BuyingStockPurchaseResult {
        val started = processedEventPort.tryStart(request.eventId, request.idempotencyKey)
        if (!started) {
            return BuyingStockPurchaseResult(orderId = null, status = PurchaseStatus.SKIPPED_DUPLICATE)
        }

        val handler = handlerFactory.getHandler<BuyingStockPurchaseCommand>(request.type)
        return runCatching {
            handler.handle(request)
        }.onSuccess {
            processedEventPort.markSuccess(request.eventId)
        }.onFailure { exception ->
            processedEventPort.markFailed(request.eventId, exception.message)
        }.getOrThrow()
    }
}

@JvmInline
value class StockId(val value: String) {
    fun toDomain(): com.example.stockpurchaseservice.domain.StockId {
        return com.example.stockpurchaseservice.domain.StockId(value)
    }
}

enum class StrategyType {
    Undefined,
    FinalPriceBatingV1,
    ;

    fun toDomain(): com.example.stockpurchaseservice.domain.StrategyType {
        return when (this) {
            FinalPriceBatingV1 -> com.example.stockpurchaseservice.domain.StrategyType.FinalPriceBatingV1
            Undefined -> com.example.stockpurchaseservice.domain.StrategyType.Undefined
        }
    }
}

sealed class BuyingStockPurchaseCommand(
    val stockId: StockId,
    val stockName: String,
    val requestAt: ZonedDateTime,
    val type: StrategyType,
    val eventId: UUID,
    val strategyId: String,
    val idempotencyKey: String,
    val strategyVersion: String,
) {
    class OfFinalPriceBatingV1(
        stockId: StockId,
        stockName: String,
        requestAt: ZonedDateTime,
        type: StrategyType,
        purchasePrice: Double,
        eventId: UUID,
        strategyId: String,
        idempotencyKey: String,
        strategyVersion: String,
        budget: Double,
        quantityPolicy: String,
    ) : BuyingStockPurchaseCommand(
        stockId = stockId,
        stockName = stockName,
        requestAt = requestAt,
        type = type,
        eventId = eventId,
        strategyId = strategyId,
        idempotencyKey = idempotencyKey,
        strategyVersion = strategyVersion,
    ) {
        internal val targetPurchasePrice: Money = Money(purchasePrice)
        internal val budget: Money = Money(budget)
        internal val quantityPolicy: String = quantityPolicy

        init {
            require(type == StrategyType.FinalPriceBatingV1)
            require(purchasePrice > 0.0) { "purchasePrice must be positive" }
            require(strategyId.isNotBlank()) { "strategyId must not be blank" }
            require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
            require(strategyVersion.isNotBlank()) { "strategyVersion must not be blank" }
        }
    }
}

data class BuyingStockPurchaseResult(
    val orderId: OrderId?,
    val status: PurchaseStatus,
)

enum class PurchaseStatus {
    CREATED,
    FAILED,
    COMPLETED,
    SKIPPED_DUPLICATE,
}

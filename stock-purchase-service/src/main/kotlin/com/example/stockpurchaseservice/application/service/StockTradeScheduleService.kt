package com.example.stockpurchaseservice.application.service

import com.example.com.example.stockpurchaseservice.domain.ExecutedStock
import com.example.com.example.stockpurchaseservice.domain.ExecutionType
import com.example.com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import com.example.common.UseCaseImpl
import com.example.common.application.event.ApplicationEvent
import com.example.stockpurchaseservice.application.port.`in`.StockTradeScheduleUseCase
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.service.strategy.toDto
import com.example.stockpurchaseservice.domain.FinalPriceBatingV1
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.SellingOrder
import com.example.stockpurchaseservice.domain.Stock
import com.example.stockpurchaseservice.domain.StrategyType
import org.springframework.context.ApplicationEventPublisher
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.collections.filter

@UseCaseImpl
internal class StockTradeScheduleService(
    private val stockOrderRepository: StockOrderRepository,
    private val marketService: MarketServicePort,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : StockTradeScheduleUseCase {

    private val executionQueue: MutableSet<ExecutedStock> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    override suspend fun executeSellOrdersByStrategies() {
        val orders = stockOrderRepository.findAllNotCompleted()
        val sellingOrderList = orders.mapNotNull { order ->
            when (order.strategyType) {
                StrategyType.Undefined -> throw RuntimeException("Undefined strategy type ${order.strategyType}")
                StrategyType.FinalPriceBatingV1 -> {
                    val strategy = FinalPriceBatingV1.of(
                        stock = Stock(id = order.stockId, name = order.stockName),
                        requestedAt = order.requestedAt,
                        purchasePrice = order.purchasePrice,
                        purchasedAt = order.purchasedAt,
                    )
                    val sellingOrder = strategy.createSellingOrder(order.id)
                    if (sellingOrder == null) println("selling order unexpected error")
                    sellingOrder
                }
            }
        }

        sellingOrderList.forEach { order ->
            stockOrderRepository.save(order)
            marketService.sellStock(order.toDto())
        }
    }

    override suspend fun executeExecutionCheck() {
        val executedStockList: List<ExecutedStock> = marketService.findExecutionListAtOneDay()
        val refinedExecutedStockList = executedStockList.filter { execution ->
            executionQueue.add(execution)
        }
        if (refinedExecutedStockList.isEmpty()) return

        val (selled, purchased) = refinedExecutedStockList.partition { it.type == ExecutionType.Selling }
        
        selled.forEach { item ->
            // TODO: selling completion logic
        }
        
        purchased.forEach { item ->
            val order = stockOrderRepository.findByExternalOrderId(item.externalOrderId)?.let { order ->
                makeSellOrderByStrategy(order)
            }
            order?.let {
                marketService.sellStock(it.toDto(quantity = item.quantity))
            }
        }
    }

    override fun initializeExecutionQueue() {
        executionQueue.clear()
    }

    private suspend fun makeSellOrderByStrategy(order: Order): SellingOrder? {
        return when (order.strategyType) {
            StrategyType.Undefined -> throw RuntimeException(
                "Undefined strategy type ${order.strategyType}",
            )
            StrategyType.FinalPriceBatingV1 -> {
                val strategy = FinalPriceBatingV1.of(
                    stock = Stock(id = order.stockId, name = order.stockName),
                    requestedAt = order.requestedAt,
                    purchasePrice = order.purchasePrice,
                    purchasedAt = order.purchasedAt,
                )
                val sellingOrder = strategy.createSellingOrder(order.id)
                if (sellingOrder == null) println("selling order unexpected error")
                sellingOrder
            }
        }
    }
}

class SellingExecutionCreatedApplicationEvent(
    val stockId: String,
    val quantity: Int,
    override val id: UUID,
    override val occurredAt: ZonedDateTime,
) : ApplicationEvent

class PurchaseExecutionCreatedApplicationEvent(
    val stockId: String,
    val quantity: Int,
    override val id: UUID,
    override val occurredAt: ZonedDateTime,
) : ApplicationEvent
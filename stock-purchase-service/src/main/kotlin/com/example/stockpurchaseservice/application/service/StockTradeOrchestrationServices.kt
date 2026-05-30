package com.example.stockpurchaseservice.application.service

import com.example.common.UseCaseImpl
import com.example.stockpurchaseservice.application.port.`in`.CreateSellOrdersByStrategyUseCase
import com.example.stockpurchaseservice.application.port.`in`.ReconcileExecutionsUseCase
import com.example.stockpurchaseservice.application.port.`in`.SimulateStockPurchaseUseCase
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.ExecutionFillDto
import com.example.stockpurchaseservice.application.port.out.ExecutionFillPort
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.service.strategy.toDto
import com.example.stockpurchaseservice.domain.ExecutedStock
import com.example.stockpurchaseservice.domain.ExecutionFill
import com.example.stockpurchaseservice.domain.ExecutionType
import com.example.stockpurchaseservice.domain.ExternalExecutionId
import com.example.stockpurchaseservice.domain.ExternalOrderId
import com.example.stockpurchaseservice.domain.FinalPriceBatingV1
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.OrderState
import com.example.stockpurchaseservice.domain.PurchaseOrder
import com.example.stockpurchaseservice.domain.SellingOrder
import com.example.stockpurchaseservice.domain.Stock
import com.example.stockpurchaseservice.domain.StockId
import com.example.stockpurchaseservice.domain.StrategyType
import com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import java.time.ZonedDateTime

@UseCaseImpl
class CreateSellOrdersByStrategyService(
    private val stockOrderRepository: StockOrderRepository,
    private val marketService: MarketServicePort,
) : CreateSellOrdersByStrategyUseCase {

    override suspend fun execute() {
        // TODO: Revisit strategy priority and same-symbol conflicts before enabling multiple active strategies.
        val sellingOrderList = stockOrderRepository.findAllNotCompleted().mapNotNull(::makeSellOrderByStrategy)
        sellingOrderList.forEach { order ->
            submitSellOrder(order)
        }
    }

    private suspend fun submitSellOrder(order: SellingOrder) {
        stockOrderRepository.save(order)
        runCatching {
            marketService.sellStock(order.toDto())
            order.changeOrderState(OrderState.SELLING_IN_PROCESS)
        }.onFailure {
            order.changeOrderState(OrderState.SUBMISSION_UNKNOWN)
        }
        stockOrderRepository.save(order)
    }
}

@UseCaseImpl
class ReconcileExecutionsService(
    private val stockOrderRepository: StockOrderRepository,
    private val marketService: MarketServicePort,
    private val executionFillPort: ExecutionFillPort,
) : ReconcileExecutionsUseCase {

    override suspend fun execute() {
        // TODO: Add durable cursor/recovery handling; saveIfNew only deduplicates observed fills.
        val executedStockList: List<ExecutedStock> = marketService.findExecutionListAtOneDay().map { it.toDomain() }
        val refinedExecutedStockList = executedStockList.filter { execution ->
            executionFillPort.saveIfNew(ExecutionFillDto.from(ExecutionFill.from(execution)))
        }
        if (refinedExecutedStockList.isEmpty()) return

        val (selled, purchased) = refinedExecutedStockList.partition { it.type == ExecutionType.Selling }
        selled.forEach {
            // Selling reconciliation is modeled here so the scheduler no longer owns the branch.
            // A later order-state redesign can apply external execution ids to the aggregate.
            // TODO: Mark selling completion from aggregated fill events instead of assuming one fill closes the order.
        }
        purchased.forEach { item ->
            // TODO: Unmatched broker executions should emit an event/log; normal fills should map to a known order.
            stockOrderRepository.findByExternalOrderId(item.externalOrderId)?.let { purchasedOrder ->
                purchasedOrder.changeOrderState(OrderState.PURCHASE_COMPLETED)
                stockOrderRepository.save(purchasedOrder)

                makeSellOrderByStrategy(purchasedOrder)?.let { sellingOrder ->
                    stockOrderRepository.save(sellingOrder)
                    runCatching {
                        // TODO: Publish submission lifecycle events once direct broker calls are split from orchestration.
                        marketService.sellStock(sellingOrder.toDto(quantity = item.quantity))
                        sellingOrder.changeOrderState(OrderState.SELLING_IN_PROCESS)
                    }.onFailure {
                        sellingOrder.changeOrderState(OrderState.SUBMISSION_UNKNOWN)
                    }
                    stockOrderRepository.save(sellingOrder)
                }
            }
        }
    }

}

@UseCaseImpl
class SimulateStockPurchaseService(
    private val stockOrderRepository: StockOrderRepository,
) : SimulateStockPurchaseUseCase {

    override suspend fun execute() {
        val waitingOrders = stockOrderRepository.findAllWithPurchaseWaiting()
        waitingOrders.forEach { order ->
            val currentPrice = getMockCurrentPrice()
            if (order.purchasePrice.price >= currentPrice) {
                val completedOrder = (order as PurchaseOrder).copy(
                    orderState = OrderState.PURCHASE_COMPLETED,
                    purchasedAt = ZonedDateTime.now(),
                )
                stockOrderRepository.save(completedOrder)
            }
        }
    }

    private fun getMockCurrentPrice(): Double {
        return (1000..2000).random().toDouble()
    }
}

private fun ExecutedStockDto.toDomain(): ExecutedStock {
    return ExecutedStock(
        stock = Stock(id = StockId(stockId), name = stockName),
        createdAt = createdAt,
        quantity = quantity,
        type = type.toDomain(),
        externalOrderId = ExternalOrderId(externalOrderId),
        externalExecutionId = ExternalExecutionId(externalExecutionId),
    )
}

private fun makeSellOrderByStrategy(order: Order): SellingOrder? {
    return when (order.strategyType) {
        StrategyType.Undefined -> throw RuntimeException("Undefined strategy type ${order.strategyType}")
        StrategyType.FinalPriceBatingV1 -> {
            val strategy = FinalPriceBatingV1.of(
                stock = Stock(id = order.stockId, name = order.stockName),
                requestedAt = order.requestedAt,
                purchasePrice = order.purchasePrice,
                strategyId = order.strategyId ?: "${order.strategyType}:${order.stockId.value}",
                purchasedAt = order.purchasedAt,
            )
            strategy.createSellingOrder(order.id)
        }
    }
}

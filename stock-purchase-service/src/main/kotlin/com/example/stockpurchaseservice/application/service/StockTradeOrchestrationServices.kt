package com.example.stockpurchaseservice.application.service

import com.example.common.UseCaseImpl
import com.example.stockpurchaseservice.application.port.`in`.CreateSellOrdersByStrategyUseCase
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.ReconcileExecutionsUseCase
import com.example.stockpurchaseservice.application.port.`in`.SimulateStockPurchaseUseCase
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentUseCase
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.ExecutionFillDto
import com.example.stockpurchaseservice.application.port.out.ExecutionFillPort
import com.example.stockpurchaseservice.application.port.out.ExecutionLookupQuery
import com.example.stockpurchaseservice.application.port.out.ExecutionQuantityModeDto
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationCursorDto
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationResultDto
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationStatePort
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OrderExecutionEventPort
import com.example.stockpurchaseservice.application.port.out.OrderFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderPartiallyFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.ReconciliationFailureAlert
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionAlert
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionDto
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
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.util.UUID

@UseCaseImpl
class CreateSellOrdersByStrategyService(
    private val stockOrderRepository: StockOrderRepository,
    private val submitOrderIntentUseCase: SubmitOrderIntentUseCase,
) : CreateSellOrdersByStrategyUseCase {

    override suspend fun execute() {
        // TODO: Revisit strategy priority and same-symbol conflicts before enabling multiple active strategies.
        val sellingOrderList = stockOrderRepository.findAllNotCompleted().mapNotNull(::makeSellOrderByStrategy)
        sellingOrderList.forEach { order ->
            submitSellOrder(order)
        }
    }

    private suspend fun submitSellOrder(order: SellingOrder) {
        submitLegacySellOrder(
            order = order,
            submitOrderIntentUseCase = submitOrderIntentUseCase,
            stockOrderRepository = stockOrderRepository,
        )
    }
}

@UseCaseImpl
class ReconcileExecutionsService(
    private val stockOrderRepository: StockOrderRepository,
    private val marketService: MarketServicePort,
    private val submitOrderIntentUseCase: SubmitOrderIntentUseCase,
    private val executionFillPort: ExecutionFillPort,
    private val orderIntentSubmissionPort: OrderIntentSubmissionPort,
    private val orderExecutionEventPort: OrderExecutionEventPort,
    private val executionReconciliationStatePort: ExecutionReconciliationStatePort,
    private val operationalAlertPort: OperationalAlertPort,
) : ReconcileExecutionsUseCase {

    override suspend fun execute() {
        val startedAt = ZonedDateTime.now()
        val previousCursor = executionReconciliationStatePort.findCursor(RECONCILIATION_SOURCE)
        executionReconciliationStatePort.markStarted(RECONCILIATION_SOURCE, startedAt)

        runCatching {
            reconcile(startedAt, previousCursor)
        }.onFailure { exception ->
            val failedAt = ZonedDateTime.now()
            executionReconciliationStatePort.markFailed(
                source = RECONCILIATION_SOURCE,
                failedAt = failedAt,
                reason = exception.message,
            )
            runCatching {
                operationalAlertPort.alertReconciliationFailed(
                    ReconciliationFailureAlert(
                        source = RECONCILIATION_SOURCE,
                        reason = exception.message,
                        failedAt = failedAt,
                    ),
                )
            }
        }.getOrThrow()
    }

    private suspend fun reconcile(
        startedAt: ZonedDateTime,
        previousCursor: ExecutionReconciliationCursorDto?,
    ) {
        val brokerExecutionList = marketService.findExecutionList(previousCursor.toLookupQuery(startedAt))
        val executedStockList = mutableListOf<ExecutedStock>()
        val refinedExecutedStockList = mutableListOf<ExecutedStock>()
        var unmatchedExecutionCount = 0
        brokerExecutionList.forEach { brokerExecution ->
            val execution = brokerExecution.toDomainForReconciliation() ?: return@forEach
            executedStockList += execution
            if (executionFillPort.saveIfNew(ExecutionFillDto.from(ExecutionFill.from(execution)))) {
                refinedExecutedStockList += execution
                val publishOutcome = publishOrderFillEventIfIntentSubmissionExists(execution)
                if (publishOutcome.unmatchedReason != null) {
                    unmatchedExecutionCount += 1
                    recordUnmatchedExecution(
                        execution = execution,
                        reason = publishOutcome.unmatchedReason,
                        observedAt = startedAt,
                    )
                }
            }
        }
        markCompleted(
            observedExecutions = executedStockList,
            savedFillCount = refinedExecutedStockList.size,
            unmatchedExecutionCount = unmatchedExecutionCount,
        )
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
                if (!isFullyFilled(item.externalOrderId.value, purchasedOrder.quantity.toLong())) return@let

                purchasedOrder.changeOrderState(OrderState.PURCHASE_COMPLETED)
                stockOrderRepository.save(purchasedOrder)

                makeSellOrderByStrategy(purchasedOrder)?.let { sellingOrder ->
                    submitLegacySellOrder(
                        order = sellingOrder,
                        submitOrderIntentUseCase = submitOrderIntentUseCase,
                        stockOrderRepository = stockOrderRepository,
                    )
                }
            }
        }
    }

    private suspend fun publishOrderFillEventIfIntentSubmissionExists(execution: ExecutedStock): FillPublishOutcome {
        val submission = orderIntentSubmissionPort.findByExternalOrderId(execution.externalOrderId.value)
            ?: return FillPublishOutcome.MISSING_SUBMISSION
        val filledPrice = execution.averageExecutionPrice ?: submission.submittedPrice
            ?: return FillPublishOutcome.MISSING_SUBMITTED_PRICE
        if (isFullyFilled(execution.externalOrderId.value, submission.quantity)) {
            orderExecutionEventPort.publishFilled(
                OrderFilledMessage(
                    eventId = execution.toEventId("ORDER_FILLED"),
                    strategyExecutionId = submission.strategyExecutionId,
                    orderIntentId = submission.orderIntentId.toString(),
                    brokerOrderId = execution.externalOrderId.value,
                    side = execution.type.toOrderIntentSide(),
                    filledPrice = filledPrice,
                    filledQuantity = execution.quantity.toLong(),
                    orderTag = submission.orderTag,
                    filledAt = execution.createdAt,
                ),
            )
        } else {
            orderExecutionEventPort.publishPartiallyFilled(
                OrderPartiallyFilledMessage(
                    eventId = execution.toEventId("ORDER_PARTIALLY_FILLED"),
                    strategyExecutionId = submission.strategyExecutionId,
                    orderIntentId = submission.orderIntentId.toString(),
                    brokerOrderId = execution.externalOrderId.value,
                    side = execution.type.toOrderIntentSide(),
                    filledPrice = filledPrice,
                    filledQuantity = execution.quantity.toLong(),
                    orderTag = submission.orderTag,
                    filledAt = execution.createdAt,
                ),
            )
        }
        return FillPublishOutcome.PUBLISHED
    }

    private suspend fun isFullyFilled(externalOrderId: String, orderQuantity: Long): Boolean {
        return executionFillPort.sumQuantityByExternalOrderId(externalOrderId) >= orderQuantity
    }

    private suspend fun recordUnmatchedExecution(
        execution: ExecutedStock,
        reason: String,
        observedAt: ZonedDateTime,
    ) {
        val unmatchedExecution = execution.toUnmatchedExecutionDto(
            reason = reason,
            observedAt = observedAt,
        )
        executionReconciliationStatePort.saveUnmatchedExecution(unmatchedExecution)
        runCatching {
            operationalAlertPort.alertUnmatchedExecution(unmatchedExecution.toAlert(RECONCILIATION_SOURCE))
        }
    }

    private suspend fun ExecutedStockDto.toDomainForReconciliation(): ExecutedStock? {
        val reconciledQuantity = when (quantityMode) {
            ExecutionQuantityModeDto.DELTA -> quantity
            ExecutionQuantityModeDto.CUMULATIVE -> {
                val alreadySavedQuantity = executionFillPort.sumQuantityByExternalOrderId(externalOrderId)
                quantity - alreadySavedQuantity.toInt()
            }
        }
        if (reconciledQuantity <= 0) return null
        return copy(quantity = reconciledQuantity).toDomain()
    }

    private suspend fun markCompleted(
        observedExecutions: List<ExecutedStock>,
        savedFillCount: Int,
        unmatchedExecutionCount: Int,
    ) {
        val lastObserved = observedExecutions.maxWithOrNull(
            compareBy<ExecutedStock> { it.createdAt }.thenBy { it.externalExecutionId.value },
        )
        executionReconciliationStatePort.markCompleted(
            source = RECONCILIATION_SOURCE,
            completedAt = ZonedDateTime.now(),
            result = ExecutionReconciliationResultDto(
                lastObservedExecutionId = lastObserved?.externalExecutionId?.value,
                lastObservedExecutionAt = lastObserved?.createdAt,
                observedExecutionCount = observedExecutions.size,
                savedFillCount = savedFillCount,
                unmatchedExecutionCount = unmatchedExecutionCount,
            ),
        )
    }

    private enum class FillPublishOutcome(val unmatchedReason: String?) {
        PUBLISHED(null),
        MISSING_SUBMISSION("NO_ORDER_INTENT_SUBMISSION"),
        MISSING_SUBMITTED_PRICE("MISSING_SUBMITTED_PRICE"),
    }

    companion object {
        private const val RECONCILIATION_SOURCE = "BROKER_EXECUTION_DAILY"
    }
}

private const val DEFAULT_RECONCILIATION_BACKFILL_DAYS = 7L

private fun ExecutionReconciliationCursorDto?.toLookupQuery(startedAt: ZonedDateTime): ExecutionLookupQuery {
    val from = this?.lastObservedExecutionAt ?: startedAt.minusDays(DEFAULT_RECONCILIATION_BACKFILL_DAYS)
    return ExecutionLookupQuery(from = from, to = startedAt)
}

private suspend fun submitLegacySellOrder(
    order: SellingOrder,
    submitOrderIntentUseCase: SubmitOrderIntentUseCase,
    stockOrderRepository: StockOrderRepository,
) {
    stockOrderRepository.save(order)
    val result = runCatching {
        submitOrderIntentUseCase.execute(order.toSellIntentCommand())
    }.getOrElse {
        order.changeOrderState(OrderState.SUBMIT_FAILED)
        stockOrderRepository.save(order)
        return
    }

    when (result.status) {
        OrderIntentSubmissionStatus.SUBMITTED,
        OrderIntentSubmissionStatus.SKIPPED_DUPLICATE -> order.changeOrderState(OrderState.SELLING_IN_PROCESS)
        OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN -> order.changeOrderState(OrderState.SUBMISSION_UNKNOWN)
        OrderIntentSubmissionStatus.REJECTED -> order.changeOrderState(OrderState.SUBMIT_FAILED)
    }
    stockOrderRepository.save(order)
}

private fun SellingOrder.toSellIntentCommand(): SubmitOrderIntentCommand {
    val strategyExecutionId = strategyId ?: "${strategyType}:${stockId.value}"
    val idempotencyKey = "legacy-sell:$strategyExecutionId:${id.value}:$quantity:${sellingPrice.price}"
    return SubmitOrderIntentCommand(
        eventId = UUID.nameUUIDFromBytes("$idempotencyKey:ORDER_INTENT_CREATED".toByteArray(StandardCharsets.UTF_8)),
        idempotencyKey = idempotencyKey,
        strategyExecutionId = strategyExecutionId,
        symbol = stockId.value,
        side = OrderIntentSide.SELL,
        orderType = OrderIntentType.LIMIT,
        price = sellingPrice.price,
        quantity = quantity.toLong(),
        orderTag = strategyType.toLegacySellOrderTag(),
        createdAt = ZonedDateTime.now(),
    )
}

private fun StrategyType.toLegacySellOrderTag(): String {
    return when (this) {
        StrategyType.Undefined -> "LEGACY_SELL"
        StrategyType.FinalPriceBatingV1 -> "FINAL_PRICE_BATING_V1_SELL"
    }
}

private fun ExecutedStock.toEventId(eventType: String): UUID {
    return UUID.nameUUIDFromBytes(
        "${externalExecutionId.value}:$eventType".toByteArray(StandardCharsets.UTF_8),
    )
}

private fun ExecutedStock.toUnmatchedExecutionDto(
    reason: String,
    observedAt: ZonedDateTime,
): UnmatchedExecutionDto {
    return UnmatchedExecutionDto(
        externalExecutionId = externalExecutionId.value,
        externalOrderId = externalOrderId.value,
        stockId = stock.id.value,
        stockName = stock.name,
        createdAt = createdAt,
        quantity = quantity,
        type = ExecutionTypeDto.from(type),
        reason = reason,
        observedAt = observedAt,
    )
}

private fun UnmatchedExecutionDto.toAlert(source: String): UnmatchedExecutionAlert {
    return UnmatchedExecutionAlert(
        source = source,
        externalExecutionId = externalExecutionId,
        externalOrderId = externalOrderId,
        stockId = stockId,
        quantity = quantity,
        type = type,
        reason = reason,
        observedAt = observedAt,
    )
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
        averageExecutionPrice = averageExecutionPrice,
    )
}

private fun makeSellOrderByStrategy(order: Order): SellingOrder? {
    if (order !is PurchaseOrder || order.orderState != OrderState.PURCHASE_COMPLETED) return null

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

private fun ExecutionType.toOrderIntentSide(): OrderIntentSide {
    return when (this) {
        ExecutionType.Purchase -> OrderIntentSide.BUY
        ExecutionType.Selling -> OrderIntentSide.SELL
    }
}

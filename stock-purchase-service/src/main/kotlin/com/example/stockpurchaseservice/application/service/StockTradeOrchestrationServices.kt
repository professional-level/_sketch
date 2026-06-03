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
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
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
import org.springframework.beans.factory.annotation.Value
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
    @Value("\${akra.order.execution-reconciliation.backfill-days:7}")
    private val reconciliationBackfillDays: Long = DEFAULT_RECONCILIATION_BACKFILL_DAYS,
) : ReconcileExecutionsUseCase {

    override suspend fun execute() {
        val startedAt = ZonedDateTime.now()
        val previousCursor = executionReconciliationStatePort.findCursor(RECONCILIATION_SOURCE)
        executionReconciliationStatePort.markStarted(RECONCILIATION_SOURCE, startedAt)

        val reconciliationResult = runCatching {
            reconcile(startedAt, previousCursor)
        }
        reconciliationResult.exceptionOrNull()?.let { exception ->
            val failedAt = ZonedDateTime.now()
            runCatching {
                executionReconciliationStatePort.markFailed(
                    source = RECONCILIATION_SOURCE,
                    failedAt = failedAt,
                    reason = exception.message,
                )
            }
            runCatching {
                operationalAlertPort.alertReconciliationFailed(
                    ReconciliationFailureAlert(
                        source = RECONCILIATION_SOURCE,
                        reason = exception.message,
                        failedAt = failedAt,
                    ),
                )
            }
        }
        reconciliationResult.getOrThrow()
    }

    private suspend fun reconcile(
        startedAt: ZonedDateTime,
        previousCursor: ExecutionReconciliationCursorDto?,
    ) {
        val brokerExecutionList = marketService.findExecutionList(
            previousCursor.toLookupQuery(startedAt, reconciliationBackfillDays),
        )
            .sortedWith(compareBy<ExecutedStockDto> { it.createdAt }.thenBy { it.externalExecutionId })
        val executedStockList = mutableListOf<ExecutedStock>()
        val refinedExecutedStockList = mutableListOf<ExecutedStock>()
        var unmatchedExecutionCount = 0
        brokerExecutionList.forEach { brokerExecution ->
            val execution = brokerExecution.toDomainForReconciliation() ?: return@forEach
            executedStockList += execution
            when (val publishPlan = resolveFillPublishPlan(execution)) {
                is FillPublishPlan.Unmatched -> {
                    unmatchedExecutionCount += 1
                    recordUnmatchedExecution(
                        execution = execution,
                        reason = publishPlan.reason,
                        observedAt = startedAt,
                    )
                }

                is FillPublishPlan.Publish -> {
                    val fill = ExecutionFillDto.from(ExecutionFill.from(execution))
                    if (executionFillPort.exists(fill.externalExecutionId)) {
                        executionReconciliationStatePort.markUnmatchedExecutionResolved(fill.externalExecutionId)
                        return@forEach
                    }

                    publishOrderFillEvent(execution, publishPlan)
                    if (executionFillPort.saveIfNew(fill)) {
                        executionReconciliationStatePort.markUnmatchedExecutionResolved(fill.externalExecutionId)
                        refinedExecutedStockList += execution
                    } else if (executionFillPort.exists(fill.externalExecutionId)) {
                        executionReconciliationStatePort.markUnmatchedExecutionResolved(fill.externalExecutionId)
                    }
                }
            }
        }
        markCompleted(
            observedBrokerExecutions = brokerExecutionList,
            savedFillCount = refinedExecutedStockList.size,
            unmatchedExecutionCount = unmatchedExecutionCount,
        )
        if (refinedExecutedStockList.isEmpty()) return

        val (selled, purchased) = refinedExecutedStockList.partition { it.type == ExecutionType.Selling }
        selled.forEach { item ->
            stockOrderRepository.findByExternalOrderId(item.externalOrderId)?.let { sellingOrder ->
                if (sellingOrder !is SellingOrder) return@let
                if (!isFullyFilled(item.externalOrderId.value, sellingOrder.quantity.toLong())) return@let

                sellingOrder.changeOrderState(OrderState.SELLING_COMPLETED)
                stockOrderRepository.save(sellingOrder)
            }
        }
        purchased.forEach { item ->
            // Legacy order completion remains for sketch orders that still predate order intent submissions.
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

    private suspend fun resolveFillPublishPlan(execution: ExecutedStock): FillPublishPlan {
        val submission = orderIntentSubmissionPort.findByExternalOrderId(execution.externalOrderId.value)
            ?: return FillPublishPlan.Unmatched("NO_ORDER_INTENT_SUBMISSION")
        val filledPrice = execution.averageExecutionPrice ?: submission.submittedPrice
            ?: return FillPublishPlan.Unmatched("MISSING_SUBMITTED_PRICE")
        val filledQuantityAfterThisExecution =
            executionFillPort.sumQuantityByExternalOrderId(execution.externalOrderId.value) + execution.quantity
        return FillPublishPlan.Publish(
            submission = submission,
            filledPrice = filledPrice,
            fullyFilled = filledQuantityAfterThisExecution >= submission.quantity,
        )
    }

    private suspend fun publishOrderFillEvent(
        execution: ExecutedStock,
        plan: FillPublishPlan.Publish,
    ) {
        if (plan.fullyFilled) {
            orderExecutionEventPort.publishFilled(
                OrderFilledMessage(
                    eventId = execution.toEventId("ORDER_FILLED"),
                    strategyExecutionId = plan.submission.strategyExecutionId,
                    orderIntentId = plan.submission.orderIntentId.toString(),
                    brokerOrderId = execution.externalOrderId.value,
                    side = execution.type.toOrderIntentSide(),
                    filledPrice = plan.filledPrice,
                    filledQuantity = execution.quantity.toLong(),
                    orderTag = plan.submission.orderTag,
                    filledAt = execution.createdAt,
                ),
            )
        } else {
            orderExecutionEventPort.publishPartiallyFilled(
                OrderPartiallyFilledMessage(
                    eventId = execution.toEventId("ORDER_PARTIALLY_FILLED"),
                    strategyExecutionId = plan.submission.strategyExecutionId,
                    orderIntentId = plan.submission.orderIntentId.toString(),
                    brokerOrderId = execution.externalOrderId.value,
                    side = execution.type.toOrderIntentSide(),
                    filledPrice = plan.filledPrice,
                    filledQuantity = execution.quantity.toLong(),
                    orderTag = plan.submission.orderTag,
                    filledAt = execution.createdAt,
                ),
            )
        }
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
        val newlyRecorded = executionReconciliationStatePort.saveUnmatchedExecution(unmatchedExecution)
        if (!newlyRecorded) return
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
        observedBrokerExecutions: List<ExecutedStockDto>,
        savedFillCount: Int,
        unmatchedExecutionCount: Int,
    ) {
        val lastObserved = observedBrokerExecutions.maxWithOrNull(
            compareBy<ExecutedStockDto> { it.createdAt }.thenBy { it.externalExecutionId },
        )
        executionReconciliationStatePort.markCompleted(
            source = RECONCILIATION_SOURCE,
            completedAt = ZonedDateTime.now(),
            result = ExecutionReconciliationResultDto(
                lastObservedExecutionId = lastObserved?.externalExecutionId,
                lastObservedExecutionAt = lastObserved?.createdAt,
                observedExecutionCount = observedBrokerExecutions.size,
                savedFillCount = savedFillCount,
                unmatchedExecutionCount = unmatchedExecutionCount,
            ),
        )
    }

    private sealed class FillPublishPlan {
        data class Publish(
            val submission: OrderIntentSubmissionDto,
            val filledPrice: Double,
            val fullyFilled: Boolean,
        ) : FillPublishPlan()

        data class Unmatched(val reason: String) : FillPublishPlan()
    }

    companion object {
        private const val RECONCILIATION_SOURCE = "BROKER_EXECUTION_DAILY"
    }
}

private const val DEFAULT_RECONCILIATION_BACKFILL_DAYS = 7L

private fun ExecutionReconciliationCursorDto?.toLookupQuery(
    startedAt: ZonedDateTime,
    backfillDays: Long,
): ExecutionLookupQuery {
    val normalizedBackfillDays = backfillDays.coerceAtLeast(0)
    val from = this?.lastObservedExecutionAt
        ?.minusDays(normalizedBackfillDays)
        ?: startedAt.minusDays(normalizedBackfillDays)
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

    val externalOrderId = result.externalOrderId?.takeIf { it.isNotBlank() }
    when (result.status) {
        OrderIntentSubmissionStatus.SUBMITTED -> {
            if (externalOrderId == null) {
                order.changeOrderState(OrderState.SUBMISSION_UNKNOWN)
            } else {
                order.changeOrderState(OrderState.SELLING_IN_PROCESS)
                stockOrderRepository.save(order, ExternalOrderId(externalOrderId))
                return
            }
        }
        OrderIntentSubmissionStatus.SKIPPED_DUPLICATE -> {
            if (externalOrderId == null) {
                order.changeOrderState(OrderState.SUBMISSION_UNKNOWN)
            } else {
                order.changeOrderState(OrderState.SELLING_IN_PROCESS)
                stockOrderRepository.save(order, ExternalOrderId(externalOrderId))
                return
            }
        }
        OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN -> order.changeOrderState(OrderState.SUBMISSION_UNKNOWN)
        OrderIntentSubmissionStatus.REJECTED -> order.changeOrderState(OrderState.SUBMIT_FAILED)
    }
    if (result.status == OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN) {
        externalOrderId?.let { externalOrderId ->
            stockOrderRepository.save(order, ExternalOrderId(externalOrderId))
            return
        }
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
            strategy.createSellingOrder(order.id)?.copy(quantity = order.quantity)
        }
    }
}

private fun ExecutionType.toOrderIntentSide(): OrderIntentSide {
    return when (this) {
        ExecutionType.Purchase -> OrderIntentSide.BUY
        ExecutionType.Selling -> OrderIntentSide.SELL
    }
}

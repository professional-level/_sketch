package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentResult
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentUseCase
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
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
import com.example.stockpurchaseservice.application.port.out.OrderCancelledMessage
import com.example.stockpurchaseservice.application.port.out.OrderFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.OrderPartiallyFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionFailureAlert
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.ReconciliationFailureAlert
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.SubmissionUnknownAlert
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionDto
import com.example.stockpurchaseservice.domain.ExternalOrderId
import com.example.stockpurchaseservice.domain.Money
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.OrderId
import com.example.stockpurchaseservice.domain.OrderState
import com.example.stockpurchaseservice.domain.PurchaseOrder
import com.example.stockpurchaseservice.domain.SellingOrder
import com.example.stockpurchaseservice.domain.StockId
import com.example.stockpurchaseservice.domain.StrategyType
import com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconcileExecutionsServiceTest {

    @Test
    fun `publishes partial fill when accumulated quantity is below submitted order quantity`() = runBlocking {
        val eventPort = FakeOrderExecutionEventPort()
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 30)),
            ),
            executionFillPort = FakeExecutionFillPort(),
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 100)),
            ),
            eventPort = eventPort,
        )

        service.execute()

        assertEquals(30, eventPort.partiallyFilled.single().filledQuantity)
        assertEquals(emptyList(), eventPort.filled)
    }

    @Test
    fun `publishes partial then filled when same reconciliation batch completes order`() = runBlocking {
        val eventPort = FakeOrderExecutionEventPort()
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(
                    execution(externalExecutionId = "exec-1", quantity = 30),
                    execution(externalExecutionId = "exec-2", quantity = 70),
                ),
            ),
            executionFillPort = FakeExecutionFillPort(),
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 100)),
            ),
            eventPort = eventPort,
        )

        service.execute()

        assertEquals(30, eventPort.partiallyFilled.single().filledQuantity)
        assertEquals(70, eventPort.filled.single().filledQuantity)
    }

    @Test
    fun `does not publish fill events for duplicate broker execution`() = runBlocking {
        val eventPort = FakeOrderExecutionEventPort()
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 30)),
            ),
            executionFillPort = FakeExecutionFillPort(duplicateExecutionIds = setOf("exec-1")),
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 100)),
            ),
            eventPort = eventPort,
        )

        service.execute()

        assertEquals(emptyList(), eventPort.partiallyFilled)
        assertEquals(emptyList(), eventPort.filled)
    }

    @Test
    fun `publishes only new delta when broker execution quantity is cumulative`() = runBlocking {
        val eventPort = FakeOrderExecutionEventPort()
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(
                    execution(
                        externalExecutionId = "broker-1:70:PURCHASE",
                        quantity = 70,
                        quantityMode = ExecutionQuantityModeDto.CUMULATIVE,
                    ),
                ),
            ),
            executionFillPort = FakeExecutionFillPort(
                initialQuantitiesByExternalOrderId = mapOf("broker-1" to 30),
            ),
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 100)),
            ),
            eventPort = eventPort,
        )

        service.execute()

        assertEquals(40, eventPort.partiallyFilled.single().filledQuantity)
        assertEquals(emptyList(), eventPort.filled)
    }

    @Test
    fun `records reconciliation cursor and unmatched executions`() = runBlocking {
        val reconciliationStatePort = FakeExecutionReconciliationStatePort()
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 30)),
            ),
            executionFillPort = FakeExecutionFillPort(),
            submissionPort = FakeOrderIntentSubmissionPort(submissions = emptyList()),
            eventPort = FakeOrderExecutionEventPort(),
            reconciliationStatePort = reconciliationStatePort,
        )

        service.execute()

        assertEquals(listOf("BROKER_EXECUTION_DAILY"), reconciliationStatePort.startedSources)
        assertEquals("exec-1", reconciliationStatePort.completed.single().lastObservedExecutionId)
        assertEquals(1, reconciliationStatePort.completed.single().observedExecutionCount)
        assertEquals(1, reconciliationStatePort.completed.single().savedFillCount)
        assertEquals(1, reconciliationStatePort.completed.single().unmatchedExecutionCount)
        assertEquals("exec-1", reconciliationStatePort.unmatched.single().externalExecutionId)
        assertEquals("NO_ORDER_INTENT_SUBMISSION", reconciliationStatePort.unmatched.single().reason)
    }

    @Test
    fun `marks reconciliation failed without completing cursor when broker lookup fails`() = runBlocking {
        val reconciliationStatePort = FakeExecutionReconciliationStatePort()
        val alertPort = FakeOperationalAlertPort()
        val service = service(
            marketPort = FakeMarketServicePort(
                failure = IllegalStateException("broker unavailable"),
            ),
            executionFillPort = FakeExecutionFillPort(),
            submissionPort = FakeOrderIntentSubmissionPort(submissions = emptyList()),
            eventPort = FakeOrderExecutionEventPort(),
            reconciliationStatePort = reconciliationStatePort,
            operationalAlertPort = alertPort,
        )

        runCatching { service.execute() }

        assertEquals(listOf("BROKER_EXECUTION_DAILY"), reconciliationStatePort.startedSources)
        assertEquals(emptyList(), reconciliationStatePort.completed)
        assertEquals(listOf<String?>("broker unavailable"), reconciliationStatePort.failedReasons)
        assertEquals("BROKER_EXECUTION_DAILY", alertPort.reconciliationFailed.single().source)
        assertEquals("broker unavailable", alertPort.reconciliationFailed.single().reason)
    }

    @Test
    fun `broker execution lookup starts from previous reconciliation cursor`() = runBlocking {
        val previousExecutionAt = ZonedDateTime.parse("2026-06-01T09:00:00+09:00")
        val marketPort = FakeMarketServicePort()
        val service = service(
            marketPort = marketPort,
            executionFillPort = FakeExecutionFillPort(),
            submissionPort = FakeOrderIntentSubmissionPort(submissions = emptyList()),
            eventPort = FakeOrderExecutionEventPort(),
            reconciliationStatePort = FakeExecutionReconciliationStatePort(
                cursor = ExecutionReconciliationCursorDto(
                    source = "BROKER_EXECUTION_DAILY",
                    lastObservedExecutionId = "exec-prev",
                    lastObservedExecutionAt = previousExecutionAt,
                ),
            ),
        )

        service.execute()

        with(marketPort.lookupQueries.single()) {
            assertEquals(previousExecutionAt, from)
            assertTrue(to >= previousExecutionAt)
        }
    }

    @Test
    fun `broker execution lookup backfills seven days when cursor is empty`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val service = service(
            marketPort = marketPort,
            executionFillPort = FakeExecutionFillPort(),
            submissionPort = FakeOrderIntentSubmissionPort(submissions = emptyList()),
            eventPort = FakeOrderExecutionEventPort(),
        )

        service.execute()

        with(marketPort.lookupQueries.single()) {
            assertEquals(7, Duration.between(from, to).toDays())
        }
    }

    @Test
    fun `legacy sell scheduler submits sell order through order intent lifecycle`() = runBlocking {
        val stockOrderRepository = FakeStockOrderRepository(
            notCompletedOrders = listOf(purchaseOrder(orderState = OrderState.PURCHASE_COMPLETED)),
        )
        val submitOrderIntentUseCase = FakeSubmitOrderIntentUseCase()
        val service = CreateSellOrdersByStrategyService(
            stockOrderRepository = stockOrderRepository,
            submitOrderIntentUseCase = submitOrderIntentUseCase,
        )

        service.execute()

        with(submitOrderIntentUseCase.commands.single()) {
            assertEquals(OrderIntentSide.SELL, side)
            assertEquals(OrderIntentType.LIMIT, orderType)
            assertEquals(10L, quantity)
            assertEquals("FINAL_PRICE_BATING_V1_SELL", orderTag)
        }
        assertEquals(
            listOf(OrderState.SELLING_WAITING, OrderState.SELLING_IN_PROCESS),
            stockOrderRepository.savedStates,
        )
    }

    @Test
    fun `legacy sell scheduler ignores existing sell orders`() = runBlocking {
        val stockOrderRepository = FakeStockOrderRepository(
            notCompletedOrders = listOf(sellingOrder(orderState = OrderState.SELLING_IN_PROCESS)),
        )
        val submitOrderIntentUseCase = FakeSubmitOrderIntentUseCase()
        val service = CreateSellOrdersByStrategyService(
            stockOrderRepository = stockOrderRepository,
            submitOrderIntentUseCase = submitOrderIntentUseCase,
        )

        service.execute()

        assertEquals(emptyList(), submitOrderIntentUseCase.commands)
        assertEquals(emptyList(), stockOrderRepository.savedStates)
    }

    @Test
    fun `completed purchase reconciliation submits follow up sell through order intent lifecycle`() = runBlocking {
        val stockOrderRepository = FakeStockOrderRepository(
            orderByExternalOrderId = mapOf(
                "broker-1" to purchaseOrder(orderState = OrderState.PURCHASE_IN_PROCESS),
            ),
        )
        val submitOrderIntentUseCase = FakeSubmitOrderIntentUseCase()
        val service = service(
            stockOrderRepository = stockOrderRepository,
            submitOrderIntentUseCase = submitOrderIntentUseCase,
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 10)),
            ),
            executionFillPort = FakeExecutionFillPort(),
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 10)),
            ),
            eventPort = FakeOrderExecutionEventPort(),
        )

        service.execute()

        with(submitOrderIntentUseCase.commands.single()) {
            assertEquals(OrderIntentSide.SELL, side)
            assertEquals(OrderIntentType.LIMIT, orderType)
            assertEquals(10L, quantity)
            assertEquals("FINAL_PRICE_BATING_V1_SELL", orderTag)
        }
        assertEquals(
            listOf(
                OrderState.PURCHASE_COMPLETED,
                OrderState.SELLING_WAITING,
                OrderState.SELLING_IN_PROCESS,
            ),
            stockOrderRepository.savedStates,
        )
    }

    private fun service(
        stockOrderRepository: StockOrderRepository = FakeStockOrderRepository(),
        submitOrderIntentUseCase: SubmitOrderIntentUseCase = FakeSubmitOrderIntentUseCase(),
        marketPort: MarketServicePort,
        executionFillPort: ExecutionFillPort,
        submissionPort: OrderIntentSubmissionPort,
        eventPort: OrderExecutionEventPort,
        reconciliationStatePort: ExecutionReconciliationStatePort = FakeExecutionReconciliationStatePort(),
        operationalAlertPort: OperationalAlertPort = FakeOperationalAlertPort(),
    ): ReconcileExecutionsService {
        return ReconcileExecutionsService(
            stockOrderRepository = stockOrderRepository,
            marketService = marketPort,
            submitOrderIntentUseCase = submitOrderIntentUseCase,
            executionFillPort = executionFillPort,
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            executionReconciliationStatePort = reconciliationStatePort,
            operationalAlertPort = operationalAlertPort,
        )
    }

    private fun execution(
        externalExecutionId: String,
        quantity: Int,
        externalOrderId: String = "broker-1",
        quantityMode: ExecutionQuantityModeDto = ExecutionQuantityModeDto.DELTA,
    ): ExecutedStockDto {
        return ExecutedStockDto(
            stockId = "TQQQ",
            stockName = "TQQQ",
            createdAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            quantity = quantity,
            type = ExecutionTypeDto.PURCHASE,
            externalOrderId = externalOrderId,
            externalExecutionId = externalExecutionId,
            quantityMode = quantityMode,
        )
    }

    private fun submission(
        quantity: Long,
        externalOrderId: String = "broker-1",
    ): OrderIntentSubmissionDto {
        return OrderIntentSubmissionDto(
            orderIntentId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            idempotencyKey = "idempotency-key",
            strategyExecutionId = "laor-v4:TQQQ",
            symbol = "TQQQ",
            side = OrderIntentSide.BUY,
            orderType = OrderIntentType.LOC,
            submittedPrice = 100.0,
            quantity = quantity,
            orderTag = "ENTRY_BUY",
            internalOrderId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            externalOrderId = externalOrderId,
            submittedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
        )
    }

    private fun purchaseOrder(
        orderState: OrderState,
    ): PurchaseOrder {
        return PurchaseOrder(
            id = OrderId(UUID.fromString("00000000-0000-0000-0000-000000000010")),
            strategyId = "legacy-final-price:TQQQ",
            stockId = StockId("TQQQ"),
            stockName = "TQQQ",
            requestedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            strategyType = StrategyType.FinalPriceBatingV1,
            purchasePrice = Money(100_000.0),
            purchasedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            quantity = 10,
            orderState = orderState,
        )
    }

    private fun sellingOrder(
        orderState: OrderState,
    ): SellingOrder {
        return SellingOrder.from(
            id = OrderId(UUID.fromString("00000000-0000-0000-0000-000000000011")),
            strategyId = "legacy-final-price:TQQQ",
            stockId = StockId("TQQQ"),
            stockName = "TQQQ",
            requestedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            strategyType = StrategyType.FinalPriceBatingV1,
            sellingPrice = Money(103_000.0),
            purchasePrice = Money(100_000.0),
            purchasedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            quantity = 10,
            orderState = orderState,
        )
    }

    private class FakeSubmitOrderIntentUseCase(
        private val status: OrderIntentSubmissionStatus = OrderIntentSubmissionStatus.SUBMITTED,
    ) : SubmitOrderIntentUseCase {
        val commands: MutableList<SubmitOrderIntentCommand> = mutableListOf()

        override suspend fun execute(command: SubmitOrderIntentCommand): SubmitOrderIntentResult {
            commands += command
            return SubmitOrderIntentResult(status)
        }
    }

    private class FakeMarketServicePort(
        private val executions: List<ExecutedStockDto> = emptyList(),
        private val failure: RuntimeException? = null,
    ) : MarketServicePort {
        val lookupQueries: MutableList<ExecutionLookupQuery> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto(externalOrderId = "broker-buy")
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto(externalOrderId = "broker-sell")
        }

        override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
            failure?.let { throw it }
            return executions
        }

        override fun findExecutionList(query: ExecutionLookupQuery): List<ExecutedStockDto> {
            lookupQueries += query
            failure?.let { throw it }
            return executions
        }

        override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
            return BrokerOrderStatusDto(status = BrokerOrderStatus.UNKNOWN)
        }
    }

    private class FakeExecutionFillPort(
        private val duplicateExecutionIds: Set<String> = emptySet(),
        private val initialQuantitiesByExternalOrderId: Map<String, Long> = emptyMap(),
    ) : ExecutionFillPort {
        private val saved: MutableList<ExecutionFillDto> = mutableListOf()

        override suspend fun saveIfNew(fill: ExecutionFillDto): Boolean {
            if (fill.externalExecutionId in duplicateExecutionIds) return false

            saved += fill
            return true
        }

        override suspend fun sumQuantityByExternalOrderId(externalOrderId: String): Long {
            return initialQuantitiesByExternalOrderId.getOrDefault(externalOrderId, 0) +
                saved.filter { it.externalOrderId == externalOrderId }.sumOf { it.quantity.toLong() }
        }
    }

    private class FakeOrderIntentSubmissionPort(
        submissions: List<OrderIntentSubmissionDto>,
    ) : OrderIntentSubmissionPort {
        private val submissionsByExternalOrderId = submissions.associateBy { it.externalOrderId }

        override suspend fun saveSubmitted(submission: OrderIntentSubmissionDto) = Unit

        override suspend fun saveUnknown(submission: OrderIntentSubmissionDto) = Unit

        override suspend fun saveRejected(submission: OrderIntentSubmissionDto) = Unit

        override suspend fun saveCancelled(submission: OrderIntentSubmissionDto) = Unit

        override suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionDto? {
            return submissionsByExternalOrderId[externalOrderId]
        }

        override suspend fun findUnknownSubmissions(): List<OrderIntentSubmissionDto> = emptyList()
    }

    private class FakeOrderExecutionEventPort : OrderExecutionEventPort {
        val partiallyFilled: MutableList<OrderPartiallyFilledMessage> = mutableListOf()
        val filled: MutableList<OrderFilledMessage> = mutableListOf()

        override suspend fun publishSubmitted(event: OrderSubmittedMessage) = Unit

        override suspend fun publishRejected(event: OrderRejectedMessage) = Unit

        override suspend fun publishCancelled(event: OrderCancelledMessage) = Unit

        override suspend fun publishFilled(event: OrderFilledMessage) {
            filled += event
        }

        override suspend fun publishPartiallyFilled(event: OrderPartiallyFilledMessage) {
            partiallyFilled += event
        }
    }

    private class FakeExecutionReconciliationStatePort(
        private val cursor: ExecutionReconciliationCursorDto? = null,
    ) : ExecutionReconciliationStatePort {
        val startedSources: MutableList<String> = mutableListOf()
        val completed: MutableList<ExecutionReconciliationResultDto> = mutableListOf()
        val failedReasons: MutableList<String?> = mutableListOf()
        val unmatched: MutableList<UnmatchedExecutionDto> = mutableListOf()

        override suspend fun findCursor(source: String): ExecutionReconciliationCursorDto? {
            return cursor?.takeIf { it.source == source }
        }

        override suspend fun markStarted(source: String, startedAt: ZonedDateTime) {
            startedSources += source
        }

        override suspend fun markCompleted(
            source: String,
            completedAt: ZonedDateTime,
            result: ExecutionReconciliationResultDto,
        ) {
            completed += result
        }

        override suspend fun markFailed(source: String, failedAt: ZonedDateTime, reason: String?) {
            failedReasons += reason
        }

        override suspend fun saveUnmatchedExecution(execution: UnmatchedExecutionDto) {
            unmatched += execution
        }
    }

    private class FakeOperationalAlertPort : OperationalAlertPort {
        val orderSubmissionFailed: MutableList<OrderSubmissionFailureAlert> = mutableListOf()
        val submissionUnknown: MutableList<SubmissionUnknownAlert> = mutableListOf()
        val reconciliationFailed: MutableList<ReconciliationFailureAlert> = mutableListOf()

        override suspend fun alertOrderSubmissionFailed(alert: OrderSubmissionFailureAlert) {
            orderSubmissionFailed += alert
        }

        override suspend fun alertSubmissionUnknown(alert: SubmissionUnknownAlert) {
            submissionUnknown += alert
        }

        override suspend fun alertReconciliationFailed(alert: ReconciliationFailureAlert) {
            reconciliationFailed += alert
        }
    }

    private class FakeStockOrderRepository(
        private val notCompletedOrders: List<Order> = emptyList(),
        private val orderByExternalOrderId: Map<String, Order> = emptyMap(),
    ) : StockOrderRepository {
        val savedStates: MutableList<OrderState> = mutableListOf()

        override suspend fun save(order: Order) {
            savedStates += order.orderState
        }

        override suspend fun save(order: Order, externalOrderId: ExternalOrderId) {
            savedStates += order.orderState
        }

        override suspend fun existsByStrategyId(strategyId: String): Boolean = false

        override suspend fun findAllNotCompleted(): List<Order> = notCompletedOrders

        override suspend fun findAllWithPurchaseWaiting(): List<Order> = emptyList()

        override suspend fun findByStockIdAndQuantity(stockId: StockId, quantity: Int): Order? = null

        override suspend fun findByExternalOrderId(externalOrderId: ExternalOrderId): Order? {
            return orderByExternalOrderId[externalOrderId.value]
        }

        override fun findById(id: OrderId): Order? = null
    }
}

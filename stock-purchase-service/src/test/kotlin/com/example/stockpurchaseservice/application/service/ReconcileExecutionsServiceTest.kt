package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.ExecutionFillDto
import com.example.stockpurchaseservice.application.port.out.ExecutionFillPort
import com.example.stockpurchaseservice.application.port.out.ExecutionQuantityModeDto
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
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionDto
import com.example.stockpurchaseservice.domain.ExternalOrderId
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.OrderId
import com.example.stockpurchaseservice.domain.StockId
import com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

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
        val service = service(
            marketPort = FakeMarketServicePort(
                failure = IllegalStateException("broker unavailable"),
            ),
            executionFillPort = FakeExecutionFillPort(),
            submissionPort = FakeOrderIntentSubmissionPort(submissions = emptyList()),
            eventPort = FakeOrderExecutionEventPort(),
            reconciliationStatePort = reconciliationStatePort,
        )

        runCatching { service.execute() }

        assertEquals(listOf("BROKER_EXECUTION_DAILY"), reconciliationStatePort.startedSources)
        assertEquals(emptyList(), reconciliationStatePort.completed)
        assertEquals(listOf<String?>("broker unavailable"), reconciliationStatePort.failedReasons)
    }

    private fun service(
        marketPort: MarketServicePort,
        executionFillPort: ExecutionFillPort,
        submissionPort: OrderIntentSubmissionPort,
        eventPort: OrderExecutionEventPort,
        reconciliationStatePort: ExecutionReconciliationStatePort = FakeExecutionReconciliationStatePort(),
    ): ReconcileExecutionsService {
        return ReconcileExecutionsService(
            stockOrderRepository = FakeStockOrderRepository(),
            marketService = marketPort,
            executionFillPort = executionFillPort,
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            executionReconciliationStatePort = reconciliationStatePort,
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

    private class FakeMarketServicePort(
        private val executions: List<ExecutedStockDto> = emptyList(),
        private val failure: RuntimeException? = null,
    ) : MarketServicePort {
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

    private class FakeExecutionReconciliationStatePort : ExecutionReconciliationStatePort {
        val startedSources: MutableList<String> = mutableListOf()
        val completed: MutableList<ExecutionReconciliationResultDto> = mutableListOf()
        val failedReasons: MutableList<String?> = mutableListOf()
        val unmatched: MutableList<UnmatchedExecutionDto> = mutableListOf()

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

    private class FakeStockOrderRepository : StockOrderRepository {
        override suspend fun save(order: Order) = Unit

        override suspend fun save(order: Order, externalOrderId: ExternalOrderId) = Unit

        override suspend fun existsByStrategyId(strategyId: String): Boolean = false

        override suspend fun findAllNotCompleted(): List<Order> = emptyList()

        override suspend fun findAllWithPurchaseWaiting(): List<Order> = emptyList()

        override suspend fun findByStockIdAndQuantity(stockId: StockId, quantity: Int): Order? = null

        override suspend fun findByExternalOrderId(externalOrderId: ExternalOrderId): Order? = null

        override fun findById(id: OrderId): Order? = null
    }
}

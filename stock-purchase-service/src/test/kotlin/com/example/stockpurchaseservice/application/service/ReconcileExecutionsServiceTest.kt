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
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionAlert
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
        val reconciliationStatePort = FakeExecutionReconciliationStatePort()
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 30)),
            ),
            executionFillPort = FakeExecutionFillPort(duplicateExecutionIds = setOf("exec-1")),
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 100)),
            ),
            eventPort = eventPort,
            reconciliationStatePort = reconciliationStatePort,
        )

        service.execute()

        assertEquals(emptyList(), eventPort.partiallyFilled)
        assertEquals(emptyList(), eventPort.filled)
        assertEquals(listOf("exec-1"), reconciliationStatePort.resolvedUnmatchedExecutionIds)
    }

    @Test
    fun `does not save fill when outbox event publish fails`() = runBlocking {
        val executionFillPort = FakeExecutionFillPort()
        val eventPort = FakeOrderExecutionEventPort(failPublish = true)
        val reconciliationStatePort = FakeExecutionReconciliationStatePort()
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 30)),
            ),
            executionFillPort = executionFillPort,
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 100)),
            ),
            eventPort = eventPort,
            reconciliationStatePort = reconciliationStatePort,
        )

        runCatching { service.execute() }

        assertEquals(emptyList<String>(), executionFillPort.savedExternalExecutionIds)
        assertEquals(emptyList<ExecutionReconciliationResultDto>(), reconciliationStatePort.completed)
        assertEquals(listOf<String?>("outbox unavailable"), reconciliationStatePort.failedReasons)
    }

    @Test
    fun `retries fill save without duplicating deterministic outbox event`() = runBlocking {
        val executionFillPort = FakeExecutionFillPort(failFirstSave = true)
        val eventPort = FakeOrderExecutionEventPort(idempotent = true)
        val firstService = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 30)),
            ),
            executionFillPort = executionFillPort,
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 100)),
            ),
            eventPort = eventPort,
        )
        val secondService = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 30)),
            ),
            executionFillPort = executionFillPort,
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 100)),
            ),
            eventPort = eventPort,
        )

        firstService.execute()
        secondService.execute()

        assertEquals(listOf("exec-1"), executionFillPort.savedExternalExecutionIds)
        assertEquals(1, eventPort.partiallyFilled.size)
    }

    @Test
    fun `marks reconciliation failed when fill save fails after outbox publish`() = runBlocking {
        val executionFillPort = FakeExecutionFillPort(
            saveFailure = IllegalStateException("fill store unavailable"),
        )
        val eventPort = FakeOrderExecutionEventPort()
        val reconciliationStatePort = FakeExecutionReconciliationStatePort()
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 30)),
            ),
            executionFillPort = executionFillPort,
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 100)),
            ),
            eventPort = eventPort,
            reconciliationStatePort = reconciliationStatePort,
        )

        runCatching { service.execute() }

        assertEquals(30, eventPort.partiallyFilled.single().filledQuantity)
        assertEquals(emptyList<String>(), executionFillPort.savedExternalExecutionIds)
        assertEquals(emptyList<ExecutionReconciliationResultDto>(), reconciliationStatePort.completed)
        assertEquals(listOf<String?>("fill store unavailable"), reconciliationStatePort.failedReasons)
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
    fun `publishes final delta as filled when cumulative broker quantity reaches submitted quantity`() = runBlocking {
        val eventPort = FakeOrderExecutionEventPort()
        val executionFillPort = FakeExecutionFillPort(
            initialQuantitiesByExternalOrderId = mapOf("broker-1" to 30),
        )
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(
                    execution(
                        externalExecutionId = "broker-1:100:PURCHASE",
                        quantity = 100,
                        quantityMode = ExecutionQuantityModeDto.CUMULATIVE,
                    ),
                ),
            ),
            executionFillPort = executionFillPort,
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 100)),
            ),
            eventPort = eventPort,
        )

        service.execute()

        assertEquals(emptyList(), eventPort.partiallyFilled)
        assertEquals(70, eventPort.filled.single().filledQuantity)
        assertEquals(listOf("broker-1:100:PURCHASE"), executionFillPort.savedExternalExecutionIds)
    }

    @Test
    fun `records reconciliation cursor and unmatched executions`() = runBlocking {
        val reconciliationStatePort = FakeExecutionReconciliationStatePort()
        val alertPort = FakeOperationalAlertPort()
        val executionFillPort = FakeExecutionFillPort()
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 30)),
            ),
            executionFillPort = executionFillPort,
            submissionPort = FakeOrderIntentSubmissionPort(submissions = emptyList()),
            eventPort = FakeOrderExecutionEventPort(),
            reconciliationStatePort = reconciliationStatePort,
            operationalAlertPort = alertPort,
        )

        service.execute()

        assertEquals(listOf("BROKER_EXECUTION_DAILY"), reconciliationStatePort.startedSources)
        assertEquals("exec-1", reconciliationStatePort.completed.single().lastObservedExecutionId)
        assertEquals(1, reconciliationStatePort.completed.single().observedExecutionCount)
        assertEquals(0, reconciliationStatePort.completed.single().savedFillCount)
        assertEquals(1, reconciliationStatePort.completed.single().unmatchedExecutionCount)
        assertEquals("exec-1", reconciliationStatePort.unmatched.single().externalExecutionId)
        assertEquals("NO_ORDER_INTENT_SUBMISSION", reconciliationStatePort.unmatched.single().reason)
        assertEquals("exec-1", alertPort.unmatchedExecution.single().externalExecutionId)
        assertEquals("NO_ORDER_INTENT_SUBMISSION", alertPort.unmatchedExecution.single().reason)
        assertEquals("BROKER_EXECUTION_DAILY", alertPort.unmatchedExecution.single().source)
        assertEquals(emptyList(), executionFillPort.savedExternalExecutionIds)
    }

    @Test
    fun `does not resend unmatched execution alert for already recorded broker execution`() = runBlocking {
        val reconciliationStatePort = FakeExecutionReconciliationStatePort(
            existingUnmatchedExecutionIds = setOf("exec-1"),
        )
        val alertPort = FakeOperationalAlertPort()
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 30)),
            ),
            executionFillPort = FakeExecutionFillPort(),
            submissionPort = FakeOrderIntentSubmissionPort(submissions = emptyList()),
            eventPort = FakeOrderExecutionEventPort(),
            reconciliationStatePort = reconciliationStatePort,
            operationalAlertPort = alertPort,
        )

        service.execute()

        assertEquals(emptyList(), reconciliationStatePort.unmatched)
        assertEquals(emptyList(), alertPort.unmatchedExecution)
        assertEquals(1, reconciliationStatePort.completed.single().unmatchedExecutionCount)
    }

    @Test
    fun `unmatched execution can be published after order submission appears`() = runBlocking {
        val executionFillPort = FakeExecutionFillPort()
        val execution = execution(externalExecutionId = "exec-1", quantity = 30)
        val reconciliationStatePort = FakeExecutionReconciliationStatePort()
        val firstEventPort = FakeOrderExecutionEventPort()
        service(
            marketPort = FakeMarketServicePort(executions = listOf(execution)),
            executionFillPort = executionFillPort,
            submissionPort = FakeOrderIntentSubmissionPort(submissions = emptyList()),
            eventPort = firstEventPort,
            reconciliationStatePort = reconciliationStatePort,
        ).execute()

        val secondEventPort = FakeOrderExecutionEventPort()
        service(
            marketPort = FakeMarketServicePort(executions = listOf(execution)),
            executionFillPort = executionFillPort,
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(submission(quantity = 100)),
            ),
            eventPort = secondEventPort,
            reconciliationStatePort = reconciliationStatePort,
        ).execute()

        assertEquals(emptyList(), firstEventPort.partiallyFilled)
        assertEquals("NO_ORDER_INTENT_SUBMISSION", reconciliationStatePort.unmatched.single().reason)
        assertEquals(30, secondEventPort.partiallyFilled.single().filledQuantity)
        assertEquals(listOf("exec-1"), executionFillPort.savedExternalExecutionIds)
        assertEquals(listOf("exec-1"), reconciliationStatePort.resolvedUnmatchedExecutionIds)
    }

    @Test
    fun `unmatched execution alert failure does not fail reconciliation`() = runBlocking {
        val reconciliationStatePort = FakeExecutionReconciliationStatePort()
        val service = service(
            marketPort = FakeMarketServicePort(
                executions = listOf(execution(externalExecutionId = "exec-1", quantity = 30)),
            ),
            executionFillPort = FakeExecutionFillPort(),
            submissionPort = FakeOrderIntentSubmissionPort(submissions = emptyList()),
            eventPort = FakeOrderExecutionEventPort(),
            reconciliationStatePort = reconciliationStatePort,
            operationalAlertPort = FakeOperationalAlertPort(failUnmatchedAlert = true),
        )

        service.execute()

        assertEquals("exec-1", reconciliationStatePort.unmatched.single().externalExecutionId)
        assertEquals(1, reconciliationStatePort.completed.single().unmatchedExecutionCount)
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
    fun `broker execution lookup starts with rolling backfill from previous reconciliation cursor`() = runBlocking {
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
            assertEquals(previousExecutionAt.minusDays(7), from)
            assertTrue(to >= previousExecutionAt)
        }
    }

    @Test
    fun `broker execution lookup uses configured rolling backfill days`() = runBlocking {
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
            reconciliationBackfillDays = 2,
        )

        service.execute()

        with(marketPort.lookupQueries.single()) {
            assertEquals(previousExecutionAt.minusDays(2), from)
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
        assertEquals(
            listOf(OrderId(UUID.fromString("00000000-0000-0000-0000-000000000010")) to "broker-sell"),
            stockOrderRepository.savedExternalOrderIds,
        )
    }

    @Test
    fun `legacy sell scheduler records broker id when submission is unknown`() = runBlocking {
        val stockOrderRepository = FakeStockOrderRepository(
            notCompletedOrders = listOf(purchaseOrder(orderState = OrderState.PURCHASE_COMPLETED)),
        )
        val submitOrderIntentUseCase = FakeSubmitOrderIntentUseCase(
            status = OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN,
            externalOrderId = "broker-unknown",
        )
        val service = CreateSellOrdersByStrategyService(
            stockOrderRepository = stockOrderRepository,
            submitOrderIntentUseCase = submitOrderIntentUseCase,
        )

        service.execute()

        assertEquals(
            listOf(OrderState.SELLING_WAITING, OrderState.SUBMISSION_UNKNOWN),
            stockOrderRepository.savedStates,
        )
        assertEquals(
            listOf(OrderId(UUID.fromString("00000000-0000-0000-0000-000000000010")) to "broker-unknown"),
            stockOrderRepository.savedExternalOrderIds,
        )
    }

    @Test
    fun `legacy sell scheduler keeps duplicate without broker id recoverable`() = runBlocking {
        val stockOrderRepository = FakeStockOrderRepository(
            notCompletedOrders = listOf(purchaseOrder(orderState = OrderState.PURCHASE_COMPLETED)),
        )
        val submitOrderIntentUseCase = FakeSubmitOrderIntentUseCase(
            status = OrderIntentSubmissionStatus.SKIPPED_DUPLICATE,
            externalOrderId = null,
        )
        val service = CreateSellOrdersByStrategyService(
            stockOrderRepository = stockOrderRepository,
            submitOrderIntentUseCase = submitOrderIntentUseCase,
        )

        service.execute()

        assertEquals(
            listOf(OrderState.SELLING_WAITING, OrderState.SUBMISSION_UNKNOWN),
            stockOrderRepository.savedStates,
        )
        assertEquals(emptyList(), stockOrderRepository.savedExternalOrderIds)
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

    @Test
    fun `selling reconciliation marks legacy sell order completed when fully filled`() = runBlocking {
        val stockOrderRepository = FakeStockOrderRepository(
            orderByExternalOrderId = mapOf(
                "broker-sell" to sellingOrder(orderState = OrderState.SELLING_IN_PROCESS),
            ),
        )
        val eventPort = FakeOrderExecutionEventPort()
        val service = service(
            stockOrderRepository = stockOrderRepository,
            marketPort = FakeMarketServicePort(
                executions = listOf(
                    execution(
                        externalExecutionId = "sell-exec-1",
                        externalOrderId = "broker-sell",
                        quantity = 10,
                        type = ExecutionTypeDto.SELLING,
                    ),
                ),
            ),
            executionFillPort = FakeExecutionFillPort(),
            submissionPort = FakeOrderIntentSubmissionPort(
                submissions = listOf(
                    submission(
                        quantity = 10,
                        externalOrderId = "broker-sell",
                        side = OrderIntentSide.SELL,
                        orderTag = "FINAL_PRICE_BATING_V1_SELL",
                    ),
                ),
            ),
            eventPort = eventPort,
        )

        service.execute()

        assertEquals(OrderIntentSide.SELL, eventPort.filled.single().side)
        assertEquals(10L, eventPort.filled.single().filledQuantity)
        assertEquals(listOf(OrderState.SELLING_COMPLETED), stockOrderRepository.savedStates)
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
        reconciliationBackfillDays: Long = 7,
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
            reconciliationBackfillDays = reconciliationBackfillDays,
        )
    }

    private fun execution(
        externalExecutionId: String,
        quantity: Int,
        externalOrderId: String = "broker-1",
        quantityMode: ExecutionQuantityModeDto = ExecutionQuantityModeDto.DELTA,
        type: ExecutionTypeDto = ExecutionTypeDto.PURCHASE,
    ): ExecutedStockDto {
        return ExecutedStockDto(
            stockId = "TQQQ",
            stockName = "TQQQ",
            createdAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
            quantity = quantity,
            type = type,
            externalOrderId = externalOrderId,
            externalExecutionId = externalExecutionId,
            quantityMode = quantityMode,
        )
    }

    private fun submission(
        quantity: Long,
        externalOrderId: String = "broker-1",
        side: OrderIntentSide = OrderIntentSide.BUY,
        orderTag: String = "ENTRY_BUY",
    ): OrderIntentSubmissionDto {
        return OrderIntentSubmissionDto(
            orderIntentId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            idempotencyKey = "idempotency-key",
            strategyExecutionId = "laor-v4:TQQQ",
            symbol = "TQQQ",
            side = side,
            orderType = OrderIntentType.LOC,
            submittedPrice = 100.0,
            quantity = quantity,
            orderTag = orderTag,
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
        private val externalOrderId: String? = "broker-sell",
    ) : SubmitOrderIntentUseCase {
        val commands: MutableList<SubmitOrderIntentCommand> = mutableListOf()

        override suspend fun execute(command: SubmitOrderIntentCommand): SubmitOrderIntentResult {
            commands += command
            return SubmitOrderIntentResult(
                status = status,
                externalOrderId = externalOrderId,
            )
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
        private val failFirstSave: Boolean = false,
        private val saveFailure: RuntimeException? = null,
    ) : ExecutionFillPort {
        private val saved: MutableList<ExecutionFillDto> = mutableListOf()
        private var saveAttemptCount: Int = 0
        val savedExternalExecutionIds: List<String>
            get() = saved.map { it.externalExecutionId }

        override suspend fun exists(externalExecutionId: String): Boolean {
            return externalExecutionId in duplicateExecutionIds || saved.any { it.externalExecutionId == externalExecutionId }
        }

        override suspend fun saveIfNew(fill: ExecutionFillDto): Boolean {
            if (exists(fill.externalExecutionId)) return false
            saveAttemptCount += 1
            if (failFirstSave && saveAttemptCount == 1) return false
            saveFailure?.let { throw it }

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

        override suspend fun saveCancelPending(submission: OrderIntentSubmissionDto) = Unit

        override suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionDto? {
            return submissionsByExternalOrderId[externalOrderId]
        }

        override suspend fun findUnknownSubmissions(): List<OrderIntentSubmissionDto> = emptyList()

        override suspend fun findCancelPendingSubmissions(): List<OrderIntentSubmissionDto> = emptyList()
    }

    private class FakeOrderExecutionEventPort(
        private val failPublish: Boolean = false,
        private val idempotent: Boolean = false,
    ) : OrderExecutionEventPort {
        val partiallyFilled: MutableList<OrderPartiallyFilledMessage> = mutableListOf()
        val filled: MutableList<OrderFilledMessage> = mutableListOf()
        private val seenEventIds: MutableSet<UUID> = mutableSetOf()

        override suspend fun publishSubmitted(event: OrderSubmittedMessage) = Unit

        override suspend fun publishRejected(event: OrderRejectedMessage) = Unit

        override suspend fun publishCancelled(event: OrderCancelledMessage) = Unit

        override suspend fun publishFilled(event: OrderFilledMessage) {
            if (failPublish) throw IllegalStateException("outbox unavailable")
            if (idempotent && !seenEventIds.add(event.eventId)) return
            filled += event
        }

        override suspend fun publishPartiallyFilled(event: OrderPartiallyFilledMessage) {
            if (failPublish) throw IllegalStateException("outbox unavailable")
            if (idempotent && !seenEventIds.add(event.eventId)) return
            partiallyFilled += event
        }
    }

    private class FakeExecutionReconciliationStatePort(
        private val cursor: ExecutionReconciliationCursorDto? = null,
        existingUnmatchedExecutionIds: Set<String> = emptySet(),
    ) : ExecutionReconciliationStatePort {
        private val unmatchedExecutionIds: MutableSet<String> = existingUnmatchedExecutionIds.toMutableSet()
        val startedSources: MutableList<String> = mutableListOf()
        val completed: MutableList<ExecutionReconciliationResultDto> = mutableListOf()
        val failedReasons: MutableList<String?> = mutableListOf()
        val unmatched: MutableList<UnmatchedExecutionDto> = mutableListOf()
        val resolvedUnmatchedExecutionIds: MutableList<String> = mutableListOf()

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

        override suspend fun saveUnmatchedExecution(execution: UnmatchedExecutionDto): Boolean {
            if (!unmatchedExecutionIds.add(execution.externalExecutionId)) return false
            unmatched += execution
            return true
        }

        override suspend fun markUnmatchedExecutionResolved(externalExecutionId: String) {
            unmatchedExecutionIds -= externalExecutionId
            resolvedUnmatchedExecutionIds += externalExecutionId
        }
    }

    private class FakeOperationalAlertPort(
        private val failUnmatchedAlert: Boolean = false,
    ) : OperationalAlertPort {
        val orderSubmissionFailed: MutableList<OrderSubmissionFailureAlert> = mutableListOf()
        val submissionUnknown: MutableList<SubmissionUnknownAlert> = mutableListOf()
        val reconciliationFailed: MutableList<ReconciliationFailureAlert> = mutableListOf()
        val unmatchedExecution: MutableList<UnmatchedExecutionAlert> = mutableListOf()

        override suspend fun alertOrderSubmissionFailed(alert: OrderSubmissionFailureAlert) {
            orderSubmissionFailed += alert
        }

        override suspend fun alertSubmissionUnknown(alert: SubmissionUnknownAlert) {
            submissionUnknown += alert
        }

        override suspend fun alertReconciliationFailed(alert: ReconciliationFailureAlert) {
            reconciliationFailed += alert
        }

        override suspend fun alertUnmatchedExecution(alert: UnmatchedExecutionAlert) {
            if (failUnmatchedAlert) throw IllegalStateException("alert sink unavailable")
            unmatchedExecution += alert
        }
    }

    private class FakeStockOrderRepository(
        private val notCompletedOrders: List<Order> = emptyList(),
        private val orderByExternalOrderId: Map<String, Order> = emptyMap(),
    ) : StockOrderRepository {
        val savedStates: MutableList<OrderState> = mutableListOf()
        val savedExternalOrderIds: MutableList<Pair<OrderId, String>> = mutableListOf()

        override suspend fun save(order: Order) {
            savedStates += order.orderState
        }

        override suspend fun save(order: Order, externalOrderId: ExternalOrderId) {
            savedStates += order.orderState
            savedExternalOrderIds += order.id to externalOrderId.value
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

package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.ExecutionFillDto
import com.example.stockpurchaseservice.application.port.out.ExecutionFillPort
import com.example.stockpurchaseservice.application.port.out.ExecutionTypeDto
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OrderCancelledMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionEventPort
import com.example.stockpurchaseservice.application.port.out.OrderFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderPartiallyFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionFailureAlert
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.ReconciliationFailureAlert
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderPort
import com.example.stockpurchaseservice.application.port.out.SubmissionUnknownAlert
import com.example.stockpurchaseservice.application.repository.OrderDto
import com.example.stockpurchaseservice.application.repository.OrderStateDto
import com.example.stockpurchaseservice.application.repository.StrategyTypeDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RecoverUnknownOrderSubmissionsServiceTest {

    @Test
    fun `recovers unknown submission to submitted event`() = runBlocking {
        val submissionPort = FakeOrderIntentSubmissionPort(listOf(submission(exchange = "NYSE")))
        val eventPort = FakeOrderExecutionEventPort()
        val marketService = FakeMarketServicePort(
            status = BrokerOrderStatusDto(
                status = BrokerOrderStatus.SUBMITTED,
                externalOrderId = "broker-1",
                checkedAt = CHECKED_AT,
            ),
        )
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = marketService,
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
        )

        service.execute()

        assertEquals("broker-1", submissionPort.submitted.single().externalOrderId)
        assertEquals("NYSE", marketService.queries.single().exchange)
        assertEquals(3L, marketService.queries.single().orderedQuantity)
        assertEquals(112.0, marketService.queries.single().submittedPrice)
        assertEquals("broker-1", eventPort.submitted.single().brokerOrderId)
        assertEquals(emptyList(), eventPort.rejected)
        assertEquals(emptyList(), eventPort.cancelled)
    }

    @Test
    fun `recovers legacy sell unknown submission to stock order mapping`() = runBlocking {
        val submissionPort = FakeOrderIntentSubmissionPort(
            listOf(
                submission(
                    idempotencyKey = "legacy-sell:legacy-final-price:TQQQ:$LEGACY_ORDER_ID:3:112.0",
                    side = OrderIntentSide.SELL,
                    orderTag = "FINAL_PRICE_BATING_V1_SELL",
                ),
            ),
        )
        val stockOrderPort = FakeStockOrderPort(
            order = orderDto(orderState = OrderStateDto.SUBMISSION_UNKNOWN),
        )
        val eventPort = FakeOrderExecutionEventPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.SUBMITTED,
                    externalOrderId = "broker-sell",
                    checkedAt = CHECKED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
            stockOrderPort = stockOrderPort,
        )

        service.execute()

        assertEquals("broker-sell", submissionPort.submitted.single().externalOrderId)
        assertEquals(OrderStateDto.SELLING_IN_PROCESS, stockOrderPort.saved.single().orderState)
        assertEquals(LEGACY_ORDER_ID to "broker-sell", stockOrderPort.savedExternalOrderIds.single())
        assertEquals("broker-sell", eventPort.submitted.single().brokerOrderId)
    }

    @Test
    fun `recovers unknown partially filled submission to submitted and partial fill event`() = runBlocking {
        val submissionPort = FakeOrderIntentSubmissionPort(listOf(submission()))
        val eventPort = FakeOrderExecutionEventPort()
        val executionFillPort = FakeExecutionFillPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.PARTIALLY_FILLED,
                    externalOrderId = "broker-1",
                    externalExecutionId = "exec-1",
                    checkedAt = CHECKED_AT,
                    orderedQuantity = 3,
                    cumulativeFilledQuantity = 1,
                    remainingQuantity = 2,
                    averageExecutionPrice = 112.5,
                    brokerReportedAt = BROKER_REPORTED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
            executionFillPort = executionFillPort,
        )

        service.execute()

        assertEquals("broker-1", submissionPort.submitted.single().externalOrderId)
        assertEquals(CHECKED_AT, submissionPort.submitted.single().lastStatusCheckedAt)
        assertEquals("broker-1", eventPort.submitted.single().brokerOrderId)
        assertEquals("broker-1", eventPort.partiallyFilled.single().brokerOrderId)
        assertEquals(OrderIntentSide.BUY, eventPort.partiallyFilled.single().side)
        assertEquals(112.5, eventPort.partiallyFilled.single().filledPrice)
        assertEquals(1L, eventPort.partiallyFilled.single().filledQuantity)
        assertEquals(BROKER_REPORTED_AT, eventPort.partiallyFilled.single().filledAt)
        assertEquals("exec-1", executionFillPort.saved.single().externalExecutionId)
        assertEquals(1, executionFillPort.saved.single().quantity)
        assertEquals(ExecutionTypeDto.PURCHASE, executionFillPort.saved.single().type)
        assertEquals(emptyList(), eventPort.rejected)
        assertEquals(emptyList(), eventPort.cancelled)
        assertEquals(emptyList(), eventPort.filled)
    }

    @Test
    fun `recovers unknown filled submission to filled event for unsaved delta`() = runBlocking {
        val submissionPort = FakeOrderIntentSubmissionPort(listOf(submission()))
        val eventPort = FakeOrderExecutionEventPort()
        val executionFillPort = FakeExecutionFillPort(
            initialQuantitiesByExternalOrderId = mapOf("broker-1" to 1L),
        )
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.FILLED,
                    externalOrderId = "broker-1",
                    externalExecutionId = "exec-3",
                    checkedAt = CHECKED_AT,
                    orderedQuantity = 3,
                    cumulativeFilledQuantity = 3,
                    remainingQuantity = 0,
                    averageExecutionPrice = 113.0,
                    brokerReportedAt = BROKER_REPORTED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
            executionFillPort = executionFillPort,
        )

        service.execute()

        assertEquals("broker-1", submissionPort.submitted.single().externalOrderId)
        assertEquals("broker-1", eventPort.filled.single().brokerOrderId)
        assertEquals(2L, eventPort.filled.single().filledQuantity)
        assertEquals(113.0, eventPort.filled.single().filledPrice)
        assertEquals(BROKER_REPORTED_AT, eventPort.filled.single().filledAt)
        assertEquals("exec-3", executionFillPort.saved.single().externalExecutionId)
        assertEquals(2, executionFillPort.saved.single().quantity)
        assertEquals(emptyList(), eventPort.partiallyFilled)
    }

    @Test
    fun `skips recovered fill event when cumulative quantity is already recorded`() = runBlocking {
        val submissionPort = FakeOrderIntentSubmissionPort(listOf(submission()))
        val eventPort = FakeOrderExecutionEventPort()
        val executionFillPort = FakeExecutionFillPort(
            initialQuantitiesByExternalOrderId = mapOf("broker-1" to 3L),
        )
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.FILLED,
                    externalOrderId = "broker-1",
                    externalExecutionId = "exec-3",
                    checkedAt = CHECKED_AT,
                    orderedQuantity = 3,
                    cumulativeFilledQuantity = 3,
                    remainingQuantity = 0,
                    averageExecutionPrice = 113.0,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
            executionFillPort = executionFillPort,
        )

        service.execute()

        assertEquals("broker-1", submissionPort.submitted.single().externalOrderId)
        assertEquals(emptyList(), eventPort.filled)
        assertEquals(emptyList(), eventPort.partiallyFilled)
        assertEquals(emptyList(), executionFillPort.saved)
    }

    @Test
    fun `recovers unknown submission to cancelled event`() = runBlocking {
        val submissionPort = FakeOrderIntentSubmissionPort(listOf(submission(externalOrderId = "broker-1")))
        val eventPort = FakeOrderExecutionEventPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.CANCELLED,
                    externalOrderId = "broker-1",
                    reason = "LOC expired",
                    checkedAt = CHECKED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
        )

        service.execute()

        assertEquals("broker-1", submissionPort.cancelled.single().externalOrderId)
        assertEquals("broker-1", eventPort.cancelled.single().brokerOrderId)
        assertEquals("LOC expired", eventPort.cancelled.single().reason)
    }

    @Test
    fun `keeps unknown submission when broker status is still unknown`() = runBlocking {
        val original = submission(externalOrderId = "broker-1")
        val submissionPort = FakeOrderIntentSubmissionPort(listOf(original))
        val eventPort = FakeOrderExecutionEventPort()
        val alertPort = FakeOperationalAlertPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.UNKNOWN,
                    externalOrderId = "broker-1",
                    reason = "not found yet",
                    checkedAt = CHECKED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = alertPort,
        )

        service.execute()

        assertEquals("not found yet", submissionPort.unknown.single().statusReason)
        assertEquals(CHECKED_AT, submissionPort.unknown.single().lastStatusCheckedAt)
        assertEquals(emptyList(), eventPort.submitted)
        assertEquals(emptyList(), eventPort.rejected)
        assertEquals(emptyList(), eventPort.cancelled)
        assertEquals(ORDER_INTENT_ID, alertPort.submissionUnknown.single().orderIntentId)
        assertEquals("not found yet", alertPort.submissionUnknown.single().reason)
    }

    @Test
    fun `keeps recovering other unknown submissions when one broker lookup fails`() = runBlocking {
        var callCount = 0
        val first = submission(externalOrderId = "broker-fail")
        val second = submission(externalOrderId = "broker-ok")
        val submissionPort = FakeOrderIntentSubmissionPort(listOf(first, second))
        val eventPort = FakeOrderExecutionEventPort()
        val alertPort = FakeOperationalAlertPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort { query ->
                callCount += 1
                if (query.externalOrderId == "broker-fail") {
                    throw RuntimeException("KIS lookup timeout")
                }
                BrokerOrderStatusDto(
                    status = BrokerOrderStatus.SUBMITTED,
                    externalOrderId = "broker-ok",
                    checkedAt = CHECKED_AT,
                )
            },
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = alertPort,
        ).apply {
            clock = Clock.fixed(Instant.parse("2026-06-02T00:00:00Z"), ZoneId.of("Asia/Seoul"))
        }

        service.execute()

        assertEquals(2, callCount)
        assertEquals("broker status lookup failed: KIS lookup timeout", submissionPort.unknown.single().statusReason)
        assertEquals(ZonedDateTime.parse("2026-06-02T09:00:00+09:00[Asia/Seoul]"), submissionPort.unknown.single().lastStatusCheckedAt)
        assertEquals("broker-ok", submissionPort.submitted.single().externalOrderId)
        assertEquals("broker-ok", eventPort.submitted.single().brokerOrderId)
        assertEquals("broker status lookup failed: KIS lookup timeout", alertPort.submissionUnknown.single().reason)
    }

    @Test
    fun `keeps cancel pending submission without republishing submitted event`() = runBlocking {
        val pending = submission(externalOrderId = "broker-1").copy(
            status = OrderIntentSubmissionStatusDto.CANCEL_PENDING,
            statusReason = "cancel request accepted by broker",
        )
        val submissionPort = FakeOrderIntentSubmissionPort(
            unknownSubmissions = emptyList(),
            cancelPendingSubmissions = listOf(pending),
        )
        val eventPort = FakeOrderExecutionEventPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.SUBMITTED,
                    externalOrderId = "broker-1",
                    checkedAt = CHECKED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
        )

        service.execute()

        assertEquals(OrderIntentSubmissionStatusDto.CANCEL_PENDING, submissionPort.cancelPending.single().status)
        assertEquals(CHECKED_AT, submissionPort.cancelPending.single().lastStatusCheckedAt)
        assertEquals(emptyList(), eventPort.submitted)
        assertEquals(emptyList(), eventPort.cancelled)
    }

    @Test
    fun `recovers cancel pending partially filled submission without republishing submitted event`() = runBlocking {
        val pending = submission(externalOrderId = "broker-1").copy(
            status = OrderIntentSubmissionStatusDto.CANCEL_PENDING,
            statusReason = "cancel request accepted by broker",
        )
        val submissionPort = FakeOrderIntentSubmissionPort(
            unknownSubmissions = emptyList(),
            cancelPendingSubmissions = listOf(pending),
        )
        val eventPort = FakeOrderExecutionEventPort()
        val executionFillPort = FakeExecutionFillPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.PARTIALLY_FILLED,
                    externalOrderId = "broker-1",
                    externalExecutionId = "cancel-exec-1",
                    checkedAt = CHECKED_AT,
                    orderedQuantity = 3,
                    cumulativeFilledQuantity = 1,
                    remainingQuantity = 2,
                    averageExecutionPrice = 112.5,
                    brokerReportedAt = BROKER_REPORTED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
            executionFillPort = executionFillPort,
        )

        service.execute()

        assertEquals(OrderIntentSubmissionStatusDto.CANCEL_PENDING, submissionPort.cancelPending.single().status)
        assertEquals(CHECKED_AT, submissionPort.cancelPending.single().lastStatusCheckedAt)
        assertEquals("broker-1", eventPort.partiallyFilled.single().brokerOrderId)
        assertEquals(1L, eventPort.partiallyFilled.single().filledQuantity)
        assertEquals("cancel-exec-1", executionFillPort.saved.single().externalExecutionId)
        assertEquals(emptyList(), eventPort.submitted)
        assertEquals(emptyList(), eventPort.filled)
        assertEquals(emptyList(), eventPort.cancelled)
    }

    @Test
    fun `recovers cancel pending submission to cancelled event`() = runBlocking {
        val pending = submission(externalOrderId = "broker-1").copy(
            status = OrderIntentSubmissionStatusDto.CANCEL_PENDING,
            statusReason = "cancel request accepted by broker",
        )
        val submissionPort = FakeOrderIntentSubmissionPort(
            unknownSubmissions = emptyList(),
            cancelPendingSubmissions = listOf(pending),
        )
        val eventPort = FakeOrderExecutionEventPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort(
                status = BrokerOrderStatusDto(
                    status = BrokerOrderStatus.CANCELLED,
                    externalOrderId = "broker-1",
                    reason = "cancel confirmed",
                    checkedAt = CHECKED_AT,
                ),
            ),
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = FakeOperationalAlertPort(),
        )

        service.execute()

        assertEquals("broker-1", submissionPort.cancelled.single().externalOrderId)
        assertEquals("broker-1", eventPort.cancelled.single().brokerOrderId)
        assertEquals("cancel confirmed", eventPort.cancelled.single().reason)
        assertEquals(emptyList(), eventPort.submitted)
    }

    @Test
    fun `keeps cancel pending when broker lookup fails`() = runBlocking {
        val pending = submission(externalOrderId = "broker-1").copy(
            status = OrderIntentSubmissionStatusDto.CANCEL_PENDING,
            statusReason = "cancel request accepted by broker",
        )
        val submissionPort = FakeOrderIntentSubmissionPort(
            unknownSubmissions = emptyList(),
            cancelPendingSubmissions = listOf(pending),
        )
        val eventPort = FakeOrderExecutionEventPort()
        val alertPort = FakeOperationalAlertPort()
        val service = RecoverUnknownOrderSubmissionsService(
            marketService = FakeMarketServicePort {
                throw RuntimeException("KIS lookup timeout")
            },
            orderIntentSubmissionPort = submissionPort,
            orderExecutionEventPort = eventPort,
            operationalAlertPort = alertPort,
        ).apply {
            clock = Clock.fixed(Instant.parse("2026-06-02T00:00:00Z"), ZoneId.of("Asia/Seoul"))
        }

        service.execute()

        assertEquals(OrderIntentSubmissionStatusDto.CANCEL_PENDING, submissionPort.cancelPending.single().status)
        assertEquals("broker status lookup failed: KIS lookup timeout", submissionPort.cancelPending.single().statusReason)
        assertEquals(ZonedDateTime.parse("2026-06-02T09:00:00+09:00[Asia/Seoul]"), submissionPort.cancelPending.single().lastStatusCheckedAt)
        assertEquals(emptyList(), eventPort.cancelled)
        assertEquals("broker status lookup failed: KIS lookup timeout", alertPort.submissionUnknown.single().reason)
    }

    private fun submission(
        externalOrderId: String? = null,
        exchange: String = "NASD",
        idempotencyKey: String = "unknown-buy",
        side: OrderIntentSide = OrderIntentSide.BUY,
        orderTag: String = "FIRST_BUY",
    ): OrderIntentSubmissionDto {
        return OrderIntentSubmissionDto(
            orderIntentId = ORDER_INTENT_ID,
            idempotencyKey = idempotencyKey,
            strategyExecutionId = "laor-v4-strategy:TQQQ",
            symbol = "TQQQ",
            exchange = exchange,
            side = side,
            orderType = OrderIntentType.LOC,
            submittedPrice = 112.0,
            quantity = 3,
            orderTag = orderTag,
            internalOrderId = INTERNAL_ORDER_ID,
            externalOrderId = externalOrderId,
            submittedAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
        )
    }

    private class FakeMarketServicePort(
        private val statusProvider: (BrokerOrderStatusQuery) -> BrokerOrderStatusDto,
    ) : MarketServicePort {
        constructor(status: BrokerOrderStatusDto) : this({ status })
        val queries: MutableList<BrokerOrderStatusQuery> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto(externalOrderId = "broker-buy")
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            return BrokerOrderSubmissionDto(externalOrderId = "broker-sell")
        }

        override fun findExecutionListAtOneDay(): List<ExecutedStockDto> = emptyList()

        override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
            queries += query
            assertEquals(ORDER_INTENT_ID, query.orderIntentId)
            assertEquals(INTERNAL_ORDER_ID, query.internalOrderId)
            return statusProvider(query)
        }
    }

    private class FakeOrderIntentSubmissionPort(
        private val unknownSubmissions: List<OrderIntentSubmissionDto>,
        private val cancelPendingSubmissions: List<OrderIntentSubmissionDto> = emptyList(),
    ) : OrderIntentSubmissionPort {
        val submitted: MutableList<OrderIntentSubmissionDto> = mutableListOf()
        val unknown: MutableList<OrderIntentSubmissionDto> = mutableListOf()
        val rejected: MutableList<OrderIntentSubmissionDto> = mutableListOf()
        val cancelled: MutableList<OrderIntentSubmissionDto> = mutableListOf()
        val cancelPending: MutableList<OrderIntentSubmissionDto> = mutableListOf()

        override suspend fun saveSubmitted(submission: OrderIntentSubmissionDto) {
            submitted += submission
        }

        override suspend fun saveUnknown(submission: OrderIntentSubmissionDto) {
            unknown += submission
        }

        override suspend fun saveRejected(submission: OrderIntentSubmissionDto) {
            rejected += submission
        }

        override suspend fun saveCancelled(submission: OrderIntentSubmissionDto) {
            cancelled += submission
        }

        override suspend fun saveCancelPending(submission: OrderIntentSubmissionDto) {
            cancelPending += submission.copy(status = OrderIntentSubmissionStatusDto.CANCEL_PENDING)
        }

        override suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionDto? = null

        override suspend fun findUnknownSubmissions(): List<OrderIntentSubmissionDto> {
            return unknownSubmissions
        }

        override suspend fun findCancelPendingSubmissions(): List<OrderIntentSubmissionDto> {
            return cancelPendingSubmissions
        }
    }

    private class FakeOrderExecutionEventPort : OrderExecutionEventPort {
        val submitted: MutableList<OrderSubmittedMessage> = mutableListOf()
        val rejected: MutableList<OrderRejectedMessage> = mutableListOf()
        val cancelled: MutableList<OrderCancelledMessage> = mutableListOf()
        val filled: MutableList<OrderFilledMessage> = mutableListOf()
        val partiallyFilled: MutableList<OrderPartiallyFilledMessage> = mutableListOf()

        override suspend fun publishSubmitted(event: OrderSubmittedMessage) {
            submitted += event
        }

        override suspend fun publishRejected(event: OrderRejectedMessage) {
            rejected += event
        }

        override suspend fun publishCancelled(event: OrderCancelledMessage) {
            cancelled += event
        }

        override suspend fun publishFilled(event: OrderFilledMessage) {
            filled += event
        }

        override suspend fun publishPartiallyFilled(event: OrderPartiallyFilledMessage) {
            partiallyFilled += event
        }
    }

    private class FakeExecutionFillPort(
        private val duplicateExecutionIds: Set<String> = emptySet(),
        private val initialQuantitiesByExternalOrderId: Map<String, Long> = emptyMap(),
    ) : ExecutionFillPort {
        val saved: MutableList<ExecutionFillDto> = mutableListOf()

        override suspend fun exists(externalExecutionId: String): Boolean {
            return externalExecutionId in duplicateExecutionIds ||
                saved.any { it.externalExecutionId == externalExecutionId }
        }

        override suspend fun saveIfNew(fill: ExecutionFillDto): Boolean {
            if (exists(fill.externalExecutionId)) return false

            saved += fill
            return true
        }

        override suspend fun sumQuantityByExternalOrderId(externalOrderId: String): Long {
            return initialQuantitiesByExternalOrderId.getOrDefault(externalOrderId, 0L) +
                saved.filter { it.externalOrderId == externalOrderId }.sumOf { it.quantity.toLong() }
        }
    }

    private fun orderDto(orderState: OrderStateDto): OrderDto {
        return OrderDto(
            id = LEGACY_ORDER_ID,
            strategyId = "legacy-final-price:TQQQ",
            stockId = "TQQQ",
            stockName = "TQQQ",
            requestedAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
            strategyType = StrategyTypeDto.FINAL_PRICE_BATING_V1,
            purchasedAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
            sellingAt = null,
            purchasePrice = 100.0,
            sellingPrice = 112.0,
            quantity = 3,
            orderState = orderState,
        )
    }

    private class FakeStockOrderPort(
        private var order: OrderDto?,
    ) : StockOrderPort {
        val saved: MutableList<OrderDto> = mutableListOf()
        val savedExternalOrderIds: MutableList<Pair<UUID, String>> = mutableListOf()

        override suspend fun findById(id: UUID): OrderDto? {
            return order?.takeIf { it.id == id }
        }

        override suspend fun save(order: OrderDto) {
            this.order = order
            saved += order
        }

        override suspend fun existsByStrategyId(strategyId: String): Boolean = false

        override suspend fun findAllWithNotCompleted(): List<OrderDto> = emptyList()

        override suspend fun findAllWithPurchaseWaiting(): List<OrderDto> = emptyList()

        override suspend fun findByStockIdAndQuantity(stockId: String, quantity: Int): OrderDto? = null

        override suspend fun saveExternalOrderId(internalOrderId: UUID, externalOrderId: String) {
            savedExternalOrderIds += internalOrderId to externalOrderId
        }

        override suspend fun findByExternalOrderId(value: String): OrderDto? {
            return savedExternalOrderIds
                .firstOrNull { it.second == value }
                ?.let { order }
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

    companion object {
        private val ORDER_INTENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private val INTERNAL_ORDER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        private val LEGACY_ORDER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")
        private val CHECKED_AT: ZonedDateTime = ZonedDateTime.parse("2026-06-02T09:00:00+09:00")
        private val BROKER_REPORTED_AT: ZonedDateTime = ZonedDateTime.parse("2026-06-02T09:01:00+09:00")
    }
}

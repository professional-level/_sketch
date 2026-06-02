package com.example.stockpurchaseservice.application.service

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderRejectedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionUnknownException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderTemporaryUnavailableException
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OrderCancelledMessage
import com.example.stockpurchaseservice.application.port.out.OrderExecutionEventPort
import com.example.stockpurchaseservice.application.port.out.OrderFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderPartiallyFilledMessage
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderRejectedMessage
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentCommand
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentResult
import com.example.stockpurchaseservice.application.port.out.OrderRiskControlPort
import com.example.stockpurchaseservice.application.port.out.OrderSubmittedMessage
import com.example.stockpurchaseservice.application.port.out.OrderTradingEnvironment
import com.example.stockpurchaseservice.application.port.out.OperationalAlertPort
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionFailureAlert
import com.example.stockpurchaseservice.application.port.out.ProcessedEventPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.ReconciliationFailureAlert
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import com.example.stockpurchaseservice.application.port.out.SubmissionUnknownAlert
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SubmitOrderIntentServiceTest {

    @Test
    fun `submits buy order intent to market port`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort()
        val submissionPort = FakeOrderIntentSubmissionPort()
        val eventPort = FakeOrderExecutionEventPort()
        val service = SubmitOrderIntentService(
            marketPort,
            processedEventPort,
            submissionPort,
            eventPort,
            FakeOrderRiskControlPort(),
            FakeOperationalAlertPort(),
        )
        val eventId = UUID.randomUUID()

        val result = service.execute(
            SubmitOrderIntentCommand(
                eventId = eventId,
                idempotencyKey = "laor-v4-strategy:TQQQ:2026-05-30:FIRST_BUY:0",
                strategyExecutionId = "laor-v4-strategy:TQQQ",
                symbol = "TQQQ",
                side = OrderIntentSide.BUY,
                orderType = OrderIntentType.LOC,
                price = 112.0,
                quantity = 3,
                orderTag = "FIRST_BUY",
                exchange = "NYSE",
                createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
            ),
        )

        assertEquals(OrderIntentSubmissionStatus.SUBMITTED, result.status)
        assertEquals("broker-buy-1", result.externalOrderId)
        assertEquals("branch-buy-1", result.branchOrderNumber)
        assertEquals(eventId, processedEventPort.succeeded.single())
        assertEquals(eventId, submissionPort.saved.single().orderIntentId)
        assertEquals(StockOrderMarket.OVERSEAS_US, submissionPort.saved.single().market)
        assertEquals("NYSE", submissionPort.saved.single().exchange)
        assertEquals("branch-buy-1", submissionPort.saved.single().branchOrderNumber)
        assertEquals("broker-buy-1", eventPort.submitted.single().brokerOrderId)
        with(marketPort.buyOrders.single()) {
            assertEquals("TQQQ", stockId)
            assertEquals(112.0, purchasePrice)
            assertEquals(3, quantity)
            assertEquals(StockOrderMarket.OVERSEAS_US, market)
            assertEquals(StockOrderType.LOC, orderType)
            assertEquals("NYSE", exchange)
        }
    }

    @Test
    fun `routes six digit symbols to domestic market`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort()
        val submissionPort = FakeOrderIntentSubmissionPort()
        val service = SubmitOrderIntentService(
            marketPort,
            processedEventPort,
            submissionPort,
            FakeOrderExecutionEventPort(),
            FakeOrderRiskControlPort(),
            FakeOperationalAlertPort(),
        )

        service.execute(
            SubmitOrderIntentCommand(
                eventId = UUID.randomUUID(),
                idempotencyKey = "domestic-buy",
                strategyExecutionId = "strategy:005930",
                symbol = "005930",
                side = OrderIntentSide.BUY,
                orderType = OrderIntentType.LIMIT,
                price = 70000.0,
                quantity = 2,
                orderTag = "BUY",
                createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
            ),
        )

        with(marketPort.buyOrders.single()) {
            assertEquals(StockOrderMarket.DOMESTIC, market)
            assertEquals(StockOrderType.LIMIT, orderType)
        }
        assertEquals(StockOrderMarket.DOMESTIC, submissionPort.saved.single().market)
    }

    @Test
    fun `skips duplicate order intent`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort(startResult = false)
        val eventPort = FakeOrderExecutionEventPort()
        val service = SubmitOrderIntentService(
            marketPort,
            processedEventPort,
            FakeOrderIntentSubmissionPort(),
            eventPort,
            FakeOrderRiskControlPort(),
            FakeOperationalAlertPort(),
        )

        val result = service.execute(
            SubmitOrderIntentCommand(
                eventId = UUID.randomUUID(),
                idempotencyKey = "duplicate",
                strategyExecutionId = "laor-v4-strategy:TQQQ",
                symbol = "TQQQ",
                side = OrderIntentSide.SELL,
                orderType = OrderIntentType.MOC,
                price = null,
                quantity = 1,
                orderTag = "REVERSE_MOC_SELL",
                createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
            ),
        )

        assertEquals(OrderIntentSubmissionStatus.SKIPPED_DUPLICATE, result.status)
        assertEquals(emptyList(), marketPort.sellOrders)
        assertEquals(emptyList(), eventPort.submitted)
    }

    @Test
    fun `returns existing broker ids when duplicate order intent was already submitted`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort(startResult = false)
        val eventPort = FakeOrderExecutionEventPort()
        val submissionPort = FakeOrderIntentSubmissionPort(
            existingSubmission = submission(
                idempotencyKey = "duplicate-submitted",
                externalOrderId = "broker-existing",
                branchOrderNumber = "branch-existing",
            ),
        )
        val service = SubmitOrderIntentService(
            marketPort,
            processedEventPort,
            submissionPort,
            eventPort,
            FakeOrderRiskControlPort(),
            FakeOperationalAlertPort(),
        )

        val result = service.execute(
            SubmitOrderIntentCommand(
                eventId = UUID.randomUUID(),
                idempotencyKey = "duplicate-submitted",
                strategyExecutionId = "laor-v4-strategy:TQQQ",
                symbol = "TQQQ",
                side = OrderIntentSide.SELL,
                orderType = OrderIntentType.LIMIT,
                price = 112.0,
                quantity = 1,
                orderTag = "TARGET_SELL",
                createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
            ),
        )

        assertEquals(OrderIntentSubmissionStatus.SKIPPED_DUPLICATE, result.status)
        assertEquals("broker-existing", result.externalOrderId)
        assertEquals("branch-existing", result.branchOrderNumber)
        assertEquals(emptyList(), marketPort.sellOrders)
        assertEquals(emptyList(), eventPort.submitted)
    }

    @Test
    fun `stores submission unknown without publishing rejected when broker result is unclear`() = runBlocking {
        val marketPort = FakeMarketServicePort(
            buyFailure = BrokerOrderSubmissionUnknownException("timeout after broker submit"),
        )
        val processedEventPort = FakeProcessedEventPort()
        val submissionPort = FakeOrderIntentSubmissionPort()
        val eventPort = FakeOrderExecutionEventPort()
        val alertPort = FakeOperationalAlertPort()
        val service = SubmitOrderIntentService(
            marketPort,
            processedEventPort,
            submissionPort,
            eventPort,
            FakeOrderRiskControlPort(),
            alertPort,
        )
        val eventId = UUID.randomUUID()

        val result = service.execute(
            SubmitOrderIntentCommand(
                eventId = eventId,
                idempotencyKey = "unknown-buy",
                strategyExecutionId = "laor-v4-strategy:TQQQ",
                symbol = "TQQQ",
                side = OrderIntentSide.BUY,
                orderType = OrderIntentType.LOC,
                price = 112.0,
                quantity = 3,
                orderTag = "FIRST_BUY",
                createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
                exchange = "NYSE",
            ),
        )

        assertEquals(OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN, result.status)
        assertEquals(eventId, processedEventPort.succeeded.single())
        assertEquals(eventId, submissionPort.unknown.single().orderIntentId)
        assertEquals(StockOrderMarket.OVERSEAS_US, submissionPort.unknown.single().market)
        assertEquals(eventId, alertPort.submissionUnknown.single().orderIntentId)
        assertEquals("timeout after broker submit", alertPort.submissionUnknown.single().reason)
        assertEquals(emptyList(), eventPort.submitted)
        assertEquals(emptyList(), eventPort.rejected)
    }

    @Test
    fun `stores broker rejection as rejected submission without marking event failed`() = runBlocking {
        val rejection = BrokerOrderRejectedException(
            message = "stock order rejected by broker: APBK001 insufficient buying power",
            brokerReturnCode = "1",
            brokerMessageCode = "APBK001",
        )
        val marketPort = FakeMarketServicePort(buyFailure = rejection)
        val processedEventPort = FakeProcessedEventPort()
        val submissionPort = FakeOrderIntentSubmissionPort()
        val eventPort = FakeOrderExecutionEventPort()
        val alertPort = FakeOperationalAlertPort()
        val service = SubmitOrderIntentService(
            marketPort,
            processedEventPort,
            submissionPort,
            eventPort,
            FakeOrderRiskControlPort(),
            alertPort,
        )
        val eventId = UUID.randomUUID()

        val result = service.execute(
            SubmitOrderIntentCommand(
                eventId = eventId,
                idempotencyKey = "broker-rejected-buy",
                strategyExecutionId = "laor-v4-strategy:TQQQ",
                symbol = "TQQQ",
                side = OrderIntentSide.BUY,
                orderType = OrderIntentType.LOC,
                price = 112.0,
                quantity = 3,
                orderTag = "FIRST_BUY",
                exchange = "NYSE",
                createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
            ),
        )

        assertEquals(OrderIntentSubmissionStatus.REJECTED, result.status)
        assertEquals(eventId, processedEventPort.succeeded.single())
        assertEquals(emptyList(), processedEventPort.failed)
        assertEquals(StockOrderMarket.OVERSEAS_US, submissionPort.rejected.single().market)
        assertEquals("stock order rejected by broker: APBK001 insufficient buying power", submissionPort.rejected.single().statusReason)
        assertEquals("stock order rejected by broker: APBK001 insufficient buying power", eventPort.rejected.single().reason)
        assertEquals(eventId, alertPort.orderSubmissionFailed.single().orderIntentId)
        assertEquals(emptyList(), eventPort.submitted)
    }

    @Test
    fun `alerts when broker submission fails clearly`() = runBlocking {
        val marketPort = FakeMarketServicePort(
            buyFailure = IllegalStateException("broker rejected request"),
        )
        val processedEventPort = FakeProcessedEventPort()
        val eventPort = FakeOrderExecutionEventPort()
        val alertPort = FakeOperationalAlertPort()
        val service = SubmitOrderIntentService(
            marketPort,
            processedEventPort,
            FakeOrderIntentSubmissionPort(),
            eventPort,
            FakeOrderRiskControlPort(),
            alertPort,
        )
        val eventId = UUID.randomUUID()

        runCatching {
            service.execute(
                SubmitOrderIntentCommand(
                    eventId = eventId,
                    idempotencyKey = "failed-buy",
                    strategyExecutionId = "laor-v4-strategy:TQQQ",
                    symbol = "TQQQ",
                    side = OrderIntentSide.BUY,
                    orderType = OrderIntentType.LOC,
                    price = 112.0,
                    quantity = 3,
                    orderTag = "FIRST_BUY",
                    createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
                ),
            )
        }

        assertEquals(eventId, alertPort.orderSubmissionFailed.single().orderIntentId)
        assertEquals("broker rejected request", alertPort.orderSubmissionFailed.single().reason)
        assertEquals("broker rejected request", eventPort.rejected.single().reason)
    }

    @Test
    fun `does not publish rejected event when broker is temporarily unavailable before submission`() = runBlocking {
        val marketPort = FakeMarketServicePort(
            buyFailure = BrokerOrderTemporaryUnavailableException("KIS broker gateway circuit is open"),
        )
        val processedEventPort = FakeProcessedEventPort()
        val eventPort = FakeOrderExecutionEventPort()
        val alertPort = FakeOperationalAlertPort()
        val service = SubmitOrderIntentService(
            marketPort,
            processedEventPort,
            FakeOrderIntentSubmissionPort(),
            eventPort,
            FakeOrderRiskControlPort(),
            alertPort,
        )
        val eventId = UUID.randomUUID()

        assertFailsWith<BrokerOrderTemporaryUnavailableException> {
            service.execute(
                SubmitOrderIntentCommand(
                    eventId = eventId,
                    idempotencyKey = "temporarily-unavailable-buy",
                    strategyExecutionId = "laor-v4-strategy:TQQQ",
                    symbol = "TQQQ",
                    side = OrderIntentSide.BUY,
                    orderType = OrderIntentType.LOC,
                    price = 112.0,
                    quantity = 3,
                    orderTag = "FIRST_BUY",
                    createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
                ),
            )
        }

        assertEquals(eventId, processedEventPort.failed.single())
        assertEquals(eventId, alertPort.orderSubmissionFailed.single().orderIntentId)
        assertEquals(emptyList(), eventPort.rejected)
        assertEquals(emptyList(), eventPort.submitted)
    }

    @Test
    fun `rejects order intent by risk policy before broker submission`() = runBlocking {
        val marketPort = FakeMarketServicePort()
        val processedEventPort = FakeProcessedEventPort()
        val submissionPort = FakeOrderIntentSubmissionPort()
        val eventPort = FakeOrderExecutionEventPort()
        val riskPort = FakeOrderRiskControlPort(
            result = OrderRiskAssessmentResult.rejected("order notional exceeds limit"),
        )
        val service = SubmitOrderIntentService(
            marketPort,
            processedEventPort,
            submissionPort,
            eventPort,
            riskPort,
            FakeOperationalAlertPort(),
        )
        val eventId = UUID.randomUUID()

        val result = service.execute(
            SubmitOrderIntentCommand(
                eventId = eventId,
                idempotencyKey = "risk-blocked-buy",
                strategyExecutionId = "laor-v4-strategy:TQQQ",
                symbol = "TQQQ",
                side = OrderIntentSide.BUY,
                orderType = OrderIntentType.LOC,
                price = 112.0,
                quantity = 3,
                orderTag = "FIRST_BUY",
                createdAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
                tradingEnvironment = OrderTradingEnvironment.LIVE,
            ),
        )

        assertEquals(OrderIntentSubmissionStatus.REJECTED, result.status)
        assertEquals(336.0, riskPort.assessed.single().estimatedNotional)
        assertEquals(OrderTradingEnvironment.LIVE, riskPort.assessed.single().expectedTradingEnvironment)
        assertEquals(StockOrderMarket.OVERSEAS_US, submissionPort.rejected.single().market)
        assertEquals(OrderTradingEnvironment.LIVE, submissionPort.rejected.single().tradingEnvironment)
        assertEquals(emptyList(), marketPort.buyOrders)
        assertEquals(eventId, processedEventPort.succeeded.single())
        assertEquals("order notional exceeds limit", submissionPort.rejected.single().statusReason)
        assertEquals("order notional exceeds limit", eventPort.rejected.single().reason)
        assertEquals(emptyList(), eventPort.submitted)
    }

    private class FakeMarketServicePort(
        private val buyFailure: RuntimeException? = null,
    ) : MarketServicePort {
        val buyOrders: MutableList<PurchaseOrderDto> = mutableListOf()
        val sellOrders: MutableList<SellingOrderDto> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            buyFailure?.let { throw it }
            buyOrders += order
            return BrokerOrderSubmissionDto(
                externalOrderId = "broker-buy-${buyOrders.size}",
                branchOrderNumber = "branch-buy-${buyOrders.size}",
            )
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            sellOrders += order
            return BrokerOrderSubmissionDto(
                externalOrderId = "broker-sell-${sellOrders.size}",
                branchOrderNumber = "branch-sell-${sellOrders.size}",
            )
        }

        override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
            return emptyList()
        }

        override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
            return BrokerOrderStatusDto(status = BrokerOrderStatus.UNKNOWN)
        }
    }

    private class FakeProcessedEventPort(
        private val startResult: Boolean = true,
    ) : ProcessedEventPort {
        val succeeded: MutableList<UUID> = mutableListOf()
        val failed: MutableList<UUID> = mutableListOf()

        override suspend fun tryStart(eventId: UUID, idempotencyKey: String): Boolean {
            return startResult
        }

        override suspend fun markSuccess(eventId: UUID) {
            succeeded += eventId
        }

        override suspend fun markFailed(eventId: UUID, reason: String?) {
            failed += eventId
        }
    }

    private fun submission(
        idempotencyKey: String,
        externalOrderId: String?,
        branchOrderNumber: String? = null,
        status: OrderIntentSubmissionStatusDto = OrderIntentSubmissionStatusDto.SUBMITTED,
    ): OrderIntentSubmissionDto {
        return OrderIntentSubmissionDto(
            orderIntentId = UUID.randomUUID(),
            idempotencyKey = idempotencyKey,
            strategyExecutionId = "laor-v4-strategy:TQQQ",
            symbol = "TQQQ",
            side = OrderIntentSide.SELL,
            orderType = OrderIntentType.LIMIT,
            submittedPrice = 112.0,
            quantity = 1,
            orderTag = "TARGET_SELL",
            internalOrderId = UUID.randomUUID(),
            externalOrderId = externalOrderId,
            branchOrderNumber = branchOrderNumber,
            submittedAt = ZonedDateTime.parse("2026-05-30T09:00:00+09:00"),
            status = status,
        )
    }

    private class FakeOrderIntentSubmissionPort(
        private val existingSubmission: OrderIntentSubmissionDto? = null,
    ) : OrderIntentSubmissionPort {
        val saved: MutableList<OrderIntentSubmissionDto> = mutableListOf()
        val unknown: MutableList<OrderIntentSubmissionDto> = mutableListOf()
        val rejected: MutableList<OrderIntentSubmissionDto> = mutableListOf()
        val cancelPending: MutableList<OrderIntentSubmissionDto> = mutableListOf()

        override suspend fun saveSubmitted(submission: OrderIntentSubmissionDto) {
            saved += submission
        }

        override suspend fun saveUnknown(submission: OrderIntentSubmissionDto) {
            unknown += submission
        }

        override suspend fun saveRejected(submission: OrderIntentSubmissionDto) {
            rejected += submission
        }

        override suspend fun saveCancelled(submission: OrderIntentSubmissionDto) = Unit

        override suspend fun saveCancelPending(submission: OrderIntentSubmissionDto) {
            cancelPending += submission
        }

        override suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionDto? {
            return saved.firstOrNull { it.externalOrderId == externalOrderId }
        }

        override suspend fun findByIdempotencyKey(idempotencyKey: String): OrderIntentSubmissionDto? {
            return existingSubmission?.takeIf { it.idempotencyKey == idempotencyKey }
        }

        override suspend fun findUnknownSubmissions(): List<OrderIntentSubmissionDto> = unknown

        override suspend fun findCancelPendingSubmissions(): List<OrderIntentSubmissionDto> = cancelPending
    }

    private class FakeOrderRiskControlPort(
        private val result: OrderRiskAssessmentResult = OrderRiskAssessmentResult.accepted(),
    ) : OrderRiskControlPort {
        val assessed: MutableList<OrderRiskAssessmentCommand> = mutableListOf()

        override suspend fun assess(command: OrderRiskAssessmentCommand): OrderRiskAssessmentResult {
            assessed += command
            return result
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

    private class FakeOrderExecutionEventPort : OrderExecutionEventPort {
        val submitted: MutableList<OrderSubmittedMessage> = mutableListOf()
        val rejected: MutableList<OrderRejectedMessage> = mutableListOf()

        override suspend fun publishSubmitted(event: OrderSubmittedMessage) {
            submitted += event
        }

        override suspend fun publishRejected(event: OrderRejectedMessage) {
            rejected += event
        }

        override suspend fun publishCancelled(event: OrderCancelledMessage) = Unit

        override suspend fun publishFilled(event: OrderFilledMessage) = Unit

        override suspend fun publishPartiallyFilled(event: OrderPartiallyFilledMessage) = Unit
    }
}

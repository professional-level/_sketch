package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionSide
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderRiskSubmissionReader
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.AccountPositionSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.CancelOrderDto
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentCommand
import com.example.stockpurchaseservice.application.port.out.OrderTradingEnvironment
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderRiskControlAdapterTest {

    @Test
    fun `rejects overseas LOC order after configured cutoff`() = runBlocking {
        val result = adapter().assess(
            command(
                orderType = OrderIntentType.LOC,
                createdAt = ZonedDateTime.parse("2026-06-01T15:51:00-04:00[America/New_York]"),
            ),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "outside OVERSEAS_US LOC order window")
    }

    @Test
    fun `accepts overseas LOC order before configured cutoff`() = runBlocking {
        val result = adapter().assess(
            command(
                orderType = OrderIntentType.LOC,
                createdAt = ZonedDateTime.parse("2026-06-01T15:49:00-04:00[America/New_York]"),
            ),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `rejects order on configured overseas market holiday`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.overseasUs.holidays = listOf("2026-06-01")
        }

        val result = adapter(properties).assess(
            command(createdAt = ZonedDateTime.parse("2026-06-01T10:00:00-04:00[America/New_York]")),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "market holiday")
    }

    @Test
    fun `applies configured early close to limit orders`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.overseasUs.earlyCloseDates = listOf("2026-06-01")
            tradingHours.overseasUs.earlyCloseTime = "13:00"
        }

        val result = adapter(properties).assess(
            command(
                orderType = OrderIntentType.LIMIT,
                createdAt = ZonedDateTime.parse("2026-06-01T13:01:00-04:00[America/New_York]"),
            ),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "allowed=09:30-13:00")
    }

    @Test
    fun `rejects order when early close configuration is invalid`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.overseasUs.earlyCloseDates = listOf("2026-06-01")
            tradingHours.overseasUs.earlyCloseTime = "invalid"
        }

        val result = adapter(properties).assess(
            command(createdAt = ZonedDateTime.parse("2026-06-01T10:00:00-04:00[America/New_York]")),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "invalid trading-hours close")
    }

    @Test
    fun `rejects buy when active pending buy notional plus new order exceeds account limit`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            maxAccountPendingBuyNotional = 1_000.0
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 850.0)

        val result = adapter(properties, reader).assess(
            command(quantity = 2, limitPrice = 100.0),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "account pending buy notional 1050.0 exceeds limit 1000.0")
    }

    @Test
    fun `accepts buy when projected pending buy notional equals account limit`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            maxAccountPendingBuyNotional = 1_000.0
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 800.0)

        val result = adapter(properties, reader).assess(
            command(quantity = 2, limitPrice = 100.0),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `does not apply pending buy exposure limit to sell orders`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            maxAccountPendingBuyNotional = 1_000.0
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 1_500.0)

        val result = adapter(properties, reader).assess(
            command(side = OrderIntentSide.SELL, orderType = OrderIntentType.LOC, quantity = 2, limitPrice = 100.0),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `rejects buy when broker account exposure plus pending and new order exceeds limit`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            maxAccountExposureNotional = 1_000.0
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 150.0)
        val marketService = FakeMarketServicePort(
            snapshot = AccountSnapshotDto(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                positions = emptyList(),
                totalEvaluationAmount = 800.0,
            ),
        )

        val result = adapter(properties, reader, marketService).assess(
            command(quantity = 1, limitPrice = 100.0),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "account exposure notional 1050.0 exceeds limit 1000.0")
        assertContains(result.reason ?: "", "(current=800.0 active=150.0 order=100.0)")
        with(marketService.queries.single()) {
            assertEquals(StockOrderMarket.OVERSEAS_US, market)
            assertEquals("NASD", exchange)
            assertEquals("USD", currency)
        }
    }

    @Test
    fun `rejects buy when active orders plus reserve exceed broker available cash`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            accountCash.enabled = true
            accountCash.reserveNotional = 25.0
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 850.0)
        val marketService = FakeMarketServicePort(
            snapshot = AccountSnapshotDto(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                positions = emptyList(),
                availableCashAmount = 1_000.0,
            ),
        )

        val result = adapter(properties, reader, marketService).assess(
            command(quantity = 2, limitPrice = 100.0),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "account cash usage 1075.0 exceeds available cash 1000.0")
        assertContains(result.reason ?: "", "(active=850.0 order=200.0 reserve=25.0)")
    }

    @Test
    fun `rejects buy when configured account cash cannot be assessed`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            accountCash.enabled = true
        }
        val marketService = FakeMarketServicePort(
            snapshot = AccountSnapshotDto(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                positions = emptyList(),
            ),
        )

        val result = adapter(properties, marketService = marketService).assess(
            command(quantity = 1, limitPrice = 100.0),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "account cash cannot be assessed")
    }

    @Test
    fun `does not apply account cash guard to sell orders`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            accountCash.enabled = true
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 2_000.0)

        val result = adapter(properties, reader).assess(
            command(side = OrderIntentSide.SELL, orderType = OrderIntentType.LOC, quantity = 2, limitPrice = 100.0),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `uses position values when account exposure summary is absent`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            maxAccountExposureNotional = 1_000.0
        }
        val marketService = FakeMarketServicePort(
            snapshot = AccountSnapshotDto(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                positions = listOf(
                    AccountPositionSnapshotDto(
                        symbol = "TQQQ",
                        stockName = "ProShares UltraPro QQQ",
                        quantity = 3,
                        currentPrice = 100.0,
                    ),
                ),
            ),
        )

        val result = adapter(properties, marketService = marketService).assess(
            command(quantity = 1, limitPrice = 100.0),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `rejects buy when configured account exposure cannot be assessed`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            maxAccountExposureNotional = 1_000.0
        }
        val marketService = FakeMarketServicePort(
            snapshot = AccountSnapshotDto(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                positions = listOf(
                    AccountPositionSnapshotDto(
                        symbol = "TQQQ",
                        stockName = "ProShares UltraPro QQQ",
                        quantity = 3,
                    ),
                ),
            ),
        )

        val result = adapter(properties, marketService = marketService).assess(
            command(quantity = 1, limitPrice = 100.0),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "account exposure cannot be assessed")
    }

    @Test
    fun `rejects strategy when expected live environment would route to mock broker`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            strategyTradingEnvironments["laor-v4-live"] = OrderTradingEnvironment.LIVE
        }

        val result = adapter(properties, overseasMockOrder = true).assess(
            command(strategyExecutionId = "laor-v4-live:TQQQ"),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "expected=LIVE actual=MOCK")
    }

    @Test
    fun `order intent trading environment overrides configured prefix policy`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            strategyTradingEnvironments["laor-v4"] = OrderTradingEnvironment.MOCK
        }

        val result = adapter(properties, overseasMockOrder = true).assess(
            command(expectedTradingEnvironment = OrderTradingEnvironment.LIVE),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "prefix=order-intent")
        assertContains(result.reason ?: "", "expected=LIVE actual=MOCK")
    }

    @Test
    fun `accepts strategy when expected live environment routes to live broker`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            strategyTradingEnvironments["laor-v4-live"] = OrderTradingEnvironment.LIVE
        }

        val result = adapter(properties, overseasMockOrder = false).assess(
            command(strategyExecutionId = "laor-v4-live:TQQQ"),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `uses longest matching strategy trading environment prefix`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            strategyTradingEnvironments["laor-v4"] = OrderTradingEnvironment.MOCK
            strategyTradingEnvironments["laor-v4:TQQQ:live"] = OrderTradingEnvironment.LIVE
        }

        val result = adapter(properties, overseasMockOrder = false).assess(
            command(strategyExecutionId = "laor-v4:TQQQ:live:cycle-1"),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `skips trading hours guard when disabled`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.enabled = false
        }

        val result = adapter(properties).assess(
            command(createdAt = ZonedDateTime.parse("2026-06-06T15:51:00-04:00[America/New_York]")),
        )

        assertTrue(result.accepted)
    }

    private fun adapter(
        properties: OrderRiskProperties = OrderRiskProperties(),
        reader: OrderRiskSubmissionReader = FakeOrderRiskSubmissionReader(),
        marketService: MarketServicePort = FakeMarketServicePort(),
        domesticMockOrder: Boolean = true,
        overseasMockOrder: Boolean = true,
    ): OrderRiskControlAdapter {
        properties.duplicateOrderKillSwitchEnabled = false
        return OrderRiskControlAdapter(
            orderRiskSubmissionReader = reader,
            marketServicePort = marketService,
            properties = properties,
            domesticMockOrder = domesticMockOrder,
            overseasMockOrder = overseasMockOrder,
        )
    }

    private fun command(
        strategyExecutionId: String = "laor-v4:TQQQ",
        side: OrderIntentSide = OrderIntentSide.BUY,
        orderType: OrderIntentType = OrderIntentType.LOC,
        market: StockOrderMarket = StockOrderMarket.OVERSEAS_US,
        quantity: Long = 1,
        limitPrice: Double? = 100.0,
        createdAt: ZonedDateTime = ZonedDateTime.parse("2026-06-01T10:00:00-04:00[America/New_York]"),
        expectedTradingEnvironment: OrderTradingEnvironment? = null,
    ): OrderRiskAssessmentCommand {
        return OrderRiskAssessmentCommand(
            orderIntentId = UUID.randomUUID(),
            internalOrderId = UUID.randomUUID(),
            idempotencyKey = UUID.randomUUID().toString(),
            strategyExecutionId = strategyExecutionId,
            symbol = "TQQQ",
            side = side,
            orderType = orderType,
            quantity = quantity,
            limitPrice = limitPrice,
            estimatedNotional = limitPrice?.let { it * quantity },
            market = market,
            orderTag = "FIRST_BUY",
            createdAt = createdAt,
            expectedTradingEnvironment = expectedTradingEnvironment,
        )
    }

    private class FakeOrderRiskSubmissionReader(
        private val activeBuyNotional: Double = 0.0,
    ) : OrderRiskSubmissionReader {
        override suspend fun countBrokerSubmittedBetween(
            from: ZonedDateTime,
            to: ZonedDateTime,
        ): Long = 0

        override suspend fun existsActiveDuplicate(
            strategyExecutionId: String,
            symbol: String,
            side: OrderIntentSubmissionSide,
            orderTag: String,
            from: ZonedDateTime,
            to: ZonedDateTime,
        ): Boolean = false

        override suspend fun sumActiveBuyNotional(): Double = activeBuyNotional
    }

    private class FakeMarketServicePort(
        private val snapshot: AccountSnapshotDto = AccountSnapshotDto(
            market = StockOrderMarket.OVERSEAS_US,
            exchange = "NASD",
            currency = "USD",
            positions = emptyList(),
            totalEvaluationAmount = 0.0,
        ),
    ) : MarketServicePort {
        val queries: MutableList<AccountSnapshotQuery> = mutableListOf()

        override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
            error("not used")
        }

        override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
            error("not used")
        }

        override fun cancelOrder(order: CancelOrderDto): BrokerOrderSubmissionDto {
            error("not used")
        }

        override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
            return emptyList()
        }

        override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
            error("not used")
        }

        override fun findAccountSnapshot(query: AccountSnapshotQuery): AccountSnapshotDto {
            queries += query
            return snapshot
        }
    }
}

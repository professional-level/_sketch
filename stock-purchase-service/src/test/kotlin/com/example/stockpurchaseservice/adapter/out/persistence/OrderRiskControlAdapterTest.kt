package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionSide
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionMarket
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderRiskSubmissionReader
import com.example.stockpurchaseservice.adapter.out.risk.ConfiguredFxRateAdapter
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
    fun `rejects order on default overseas us market holiday`() = runBlocking {
        val result = adapter().assess(
            command(createdAt = ZonedDateTime.parse("2026-07-03T10:00:00-04:00[America/New_York]")),
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
    fun `moves loc cutoff earlier on date specific early close`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.overseasUs.earlyCloseDates = listOf("2026-06-01")
            tradingHours.overseasUs.earlyCloseTime = "13:00"
            tradingHours.overseasUs.earlyCloseTimes["2026-06-01"] = "12:30"
            tradingHours.overseasUs.locCutoff = "15:50"
        }

        val result = adapter(properties).assess(
            command(
                orderType = OrderIntentType.LOC,
                createdAt = ZonedDateTime.parse("2026-06-01T12:31:00-04:00[America/New_York]"),
            ),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "allowed=09:30-12:20")
    }

    @Test
    fun `accepts date specific early close order before override cutoff`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.overseasUs.earlyCloseTimes["2026-06-01"] = "12:30"
        }

        val result = adapter(properties).assess(
            command(
                orderType = OrderIntentType.LIMIT,
                createdAt = ZonedDateTime.parse("2026-06-01T12:29:00-04:00[America/New_York]"),
            ),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `moves loc cutoff earlier on default overseas us early close`() = runBlocking {
        val result = adapter().assess(
            command(
                orderType = OrderIntentType.LOC,
                createdAt = ZonedDateTime.parse("2026-11-27T12:51:00-05:00[America/New_York]"),
            ),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "allowed=09:30-12:50")
    }

    @Test
    fun `accepts loc before adjusted default overseas us early close cutoff`() = runBlocking {
        val result = adapter().assess(
            command(
                orderType = OrderIntentType.LOC,
                createdAt = ZonedDateTime.parse("2026-11-27T12:49:00-05:00[America/New_York]"),
            ),
        )

        assertTrue(result.accepted)
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
    fun `converts domestic order notional to risk base currency before applying order limit`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.enabled = false
            maxOrderNotional = 1_000.0
            currencyConversion.ratesToBase["KRW"] = 0.001
        }

        val result = adapter(properties).assess(
            command(
                symbol = "005930",
                market = StockOrderMarket.DOMESTIC,
                quantity = 1,
                limitPrice = 1_500_000.0,
            ),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "order notional 1500.0 USD exceeds limit 1000.0 USD")
        assertContains(result.reason ?: "", "(raw=1500000.0 KRW rate=0.001)")
    }

    @Test
    fun `rejects domestic order risk check when required FX rate is absent`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.enabled = false
            maxOrderNotional = 1_000.0
        }

        val result = adapter(properties).assess(
            command(
                symbol = "005930",
                market = StockOrderMarket.DOMESTIC,
                quantity = 1,
                limitPrice = 100_000.0,
            ),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "missing FX rate from KRW to USD")
    }

    @Test
    fun `matches symbol order notional limit case insensitively after trimming`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            symbolMaxOrderNotional[" tqqq "] = 50.0
        }

        val result = adapter(properties).assess(
            command(symbol = "TQQQ", quantity = 1, limitPrice = 100.0),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "order notional 100.0 exceeds limit 50.0")
        assertContains(result.reason ?: "", "for symbol TQQQ")
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
    fun `counts daily orders using market trading day window`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.enabled = false
            maxDailyOrderCount = 10
        }
        val reader = FakeOrderRiskSubmissionReader()

        val result = adapter(properties, reader).assess(
            command(createdAt = ZonedDateTime.parse("2026-06-02T08:00:00+09:00[Asia/Seoul]")),
        )

        assertTrue(result.accepted)
        val (from, to) = reader.countBrokerSubmittedWindows.single()
        assertEquals(ZonedDateTime.parse("2026-06-01T00:00:00-04:00[America/New_York]"), from)
        assertEquals(ZonedDateTime.parse("2026-06-02T00:00:00-04:00[America/New_York]"), to)
        assertEquals(listOf(OrderIntentSubmissionMarket.OVERSEAS_US), reader.countBrokerSubmittedMarkets)
    }

    @Test
    fun `checks duplicate orders using market trading day window`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.enabled = false
            duplicateOrderKillSwitchEnabled = true
        }
        val reader = FakeOrderRiskSubmissionReader(duplicateExists = true)

        val result = adapter(
            properties = properties,
            reader = reader,
            disableDuplicateOrderKillSwitch = false,
        ).assess(
            command(createdAt = ZonedDateTime.parse("2026-06-02T08:00:00+09:00[Asia/Seoul]")),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "duplicate active order blocked")
        val (from, to) = reader.activeDuplicateWindows.single()
        assertEquals(ZonedDateTime.parse("2026-06-01T00:00:00-04:00[America/New_York]"), from)
        assertEquals(ZonedDateTime.parse("2026-06-02T00:00:00-04:00[America/New_York]"), to)
        assertEquals(listOf(OrderIntentSubmissionMarket.OVERSEAS_US), reader.activeDuplicateMarkets)
    }

    @Test
    fun `converts domestic pending buy notional to risk base currency`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.enabled = false
            maxAccountPendingBuyNotional = 1_000.0
            currencyConversion.ratesToBase["KRW"] = 0.001
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 800_000.0)

        val result = adapter(properties, reader).assess(
            command(
                symbol = "005930",
                market = StockOrderMarket.DOMESTIC,
                quantity = 1,
                limitPrice = 300_000.0,
            ),
        )

        assertFalse(result.accepted)
        assertEquals(OrderIntentSubmissionMarket.DOMESTIC, reader.activeBuyNotionalMarkets.single())
        assertContains(result.reason ?: "", "account pending buy notional 1100.0 exceeds limit 1000.0 USD")
        assertContains(result.reason ?: "", "(active=800.0 USD order=300.0 USD)")
    }

    @Test
    fun `does not apply pending buy exposure limit to sell orders`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            maxAccountPendingBuyNotional = 1_000.0
            sellPosition.enabled = false
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
    fun `converts broker account exposure to risk base currency`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.enabled = false
            maxAccountExposureNotional = 1_000.0
            currencyConversion.ratesToBase["KRW"] = 0.001
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 150_000.0)
        val marketService = FakeMarketServicePort(
            snapshot = AccountSnapshotDto(
                market = StockOrderMarket.DOMESTIC,
                exchange = "KRX",
                currency = "KRW",
                positions = emptyList(),
                totalEvaluationAmount = 800_000.0,
            ),
        )

        val result = adapter(properties, reader, marketService).assess(
            command(
                symbol = "005930",
                market = StockOrderMarket.DOMESTIC,
                quantity = 1,
                limitPrice = 100_000.0,
            ),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "account exposure notional 1050.0 exceeds limit 1000.0 USD")
        assertContains(result.reason ?: "", "(current=800.0 USD active=150.0 USD order=100.0 USD)")
    }

    @Test
    fun `rejects buy when active orders plus reserve exceed broker orderable cash`() = runBlocking {
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
                availableCashAmount = 5_000.0,
                orderableCashAmount = 1_000.0,
            ),
        )

        val result = adapter(properties, reader, marketService).assess(
            command(quantity = 2, limitPrice = 100.0),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "account cash usage 1075.0 exceeds orderable cash 1000.0")
        assertContains(result.reason ?: "", "(active=850.0 order=200.0 reserve=25.0)")
    }

    @Test
    fun `converts broker orderable cash to risk base currency`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            tradingHours.enabled = false
            accountCash.enabled = true
            accountCash.reserveNotional = 10.0
            currencyConversion.ratesToBase["KRW"] = 0.001
        }
        val reader = FakeOrderRiskSubmissionReader(activeBuyNotional = 100_000.0)
        val marketService = FakeMarketServicePort(
            snapshot = AccountSnapshotDto(
                market = StockOrderMarket.DOMESTIC,
                exchange = "KRX",
                currency = "KRW",
                positions = emptyList(),
                cashCurrency = "KRW",
                orderableCashAmount = 200_000.0,
            ),
        )

        val result = adapter(properties, reader, marketService).assess(
            command(
                symbol = "005930",
                market = StockOrderMarket.DOMESTIC,
                quantity = 1,
                limitPrice = 100_000.0,
            ),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "account cash usage 210.0 exceeds orderable cash 200.0 USD")
        assertContains(result.reason ?: "", "(active=100.0 USD order=100.0 USD reserve=10.0)")
    }

    @Test
    fun `uses legacy available cash when broker snapshot has no orderable cash bucket`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            accountCash.enabled = true
        }
        val marketService = FakeMarketServicePort(
            snapshot = AccountSnapshotDto(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                positions = emptyList(),
                availableCashAmount = 1_000.0,
            ),
        )

        val result = adapter(properties, marketService = marketService).assess(
            command(quantity = 1, limitPrice = 100.0),
        )

        assertTrue(result.accepted)
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
            sellPosition.enabled = false
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
    fun `rejects sell when order quantity exceeds broker position`() = runBlocking {
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

        val result = adapter(marketService = marketService).assess(
            command(side = OrderIntentSide.SELL, quantity = 4),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "sell quantity 4 exceeds broker position 3")
    }

    @Test
    fun `rejects sell when active sell orders plus new order exceed broker position`() = runBlocking {
        val reader = FakeOrderRiskSubmissionReader(activeSellQuantity = 3)
        val marketService = FakeMarketServicePort(
            snapshot = AccountSnapshotDto(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                positions = listOf(
                    AccountPositionSnapshotDto(
                        symbol = "TQQQ",
                        stockName = "ProShares UltraPro QQQ",
                        quantity = 5,
                    ),
                ),
            ),
        )

        val result = adapter(reader = reader, marketService = marketService).assess(
            command(side = OrderIntentSide.SELL, quantity = 3),
        )

        assertFalse(result.accepted)
        assertEquals(listOf("TQQQ" to OrderIntentSubmissionMarket.OVERSEAS_US), reader.activeSellQuantityRequests)
        assertContains(result.reason ?: "", "sell quantity 6 exceeds broker position 5")
        assertContains(result.reason ?: "", "(active=3 order=3)")
    }

    @Test
    fun `accepts sell when broker position covers active and new sell quantities`() = runBlocking {
        val reader = FakeOrderRiskSubmissionReader(activeSellQuantity = 2)
        val marketService = FakeMarketServicePort(
            snapshot = AccountSnapshotDto(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = "NASD",
                currency = "USD",
                positions = listOf(
                    AccountPositionSnapshotDto(
                        symbol = "tqqq",
                        stockName = "ProShares UltraPro QQQ",
                        quantity = 5,
                    ),
                ),
            ),
        )

        val result = adapter(reader = reader, marketService = marketService).assess(
            command(side = OrderIntentSide.SELL, quantity = 3),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `rejects sell when broker position is missing`() = runBlocking {
        val result = adapter().assess(
            command(side = OrderIntentSide.SELL, quantity = 1),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "no broker position for TQQQ")
    }

    @Test
    fun `rejects strategy outside enabled prefix allow list`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            enabledStrategyPrefixes = listOf("laor-v4-live")
        }

        val result = adapter(properties).assess(
            command(strategyExecutionId = "laor-v4-paper:TQQQ"),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "strategy not enabled by risk policy")
        assertContains(result.reason ?: "", "enabledPrefixes=laor-v4-live")
    }

    @Test
    fun `accepts strategy inside enabled prefix allow list`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            enabledStrategyPrefixes = listOf("laor-v4-paper")
        }

        val result = adapter(properties).assess(
            command(strategyExecutionId = "laor-v4-paper:TQQQ"),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun `disabled strategy prefix wins over enabled allow list`() = runBlocking {
        val properties = OrderRiskProperties().apply {
            enabledStrategyPrefixes = listOf("laor-v4")
            disabledStrategyPrefixes = listOf("laor-v4-paper")
        }

        val result = adapter(properties).assess(
            command(strategyExecutionId = "laor-v4-paper:TQQQ"),
        )

        assertFalse(result.accepted)
        assertContains(result.reason ?: "", "strategy disabled by risk policy")
        assertContains(result.reason ?: "", "prefix=laor-v4-paper")
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
        disableDuplicateOrderKillSwitch: Boolean = true,
    ): OrderRiskControlAdapter {
        if (disableDuplicateOrderKillSwitch) {
            properties.duplicateOrderKillSwitchEnabled = false
        }
        return OrderRiskControlAdapter(
            orderRiskSubmissionReader = reader,
            marketServicePort = marketService,
            fxRatePort = ConfiguredFxRateAdapter(properties),
            properties = properties,
            domesticMockOrder = domesticMockOrder,
            overseasMockOrder = overseasMockOrder,
        )
    }

    private fun command(
        strategyExecutionId: String = "laor-v4:TQQQ",
        symbol: String = "TQQQ",
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
            symbol = symbol,
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
        private val activeSellQuantity: Long = 0,
        private val brokerSubmittedCount: Long = 0,
        private val duplicateExists: Boolean = false,
    ) : OrderRiskSubmissionReader {
        val activeBuyNotionalMarkets: MutableList<OrderIntentSubmissionMarket> = mutableListOf()
        val activeSellQuantityRequests: MutableList<Pair<String, OrderIntentSubmissionMarket>> = mutableListOf()
        val countBrokerSubmittedMarkets: MutableList<OrderIntentSubmissionMarket> = mutableListOf()
        val countBrokerSubmittedWindows: MutableList<Pair<ZonedDateTime, ZonedDateTime>> = mutableListOf()
        val activeDuplicateMarkets: MutableList<OrderIntentSubmissionMarket> = mutableListOf()
        val activeDuplicateWindows: MutableList<Pair<ZonedDateTime, ZonedDateTime>> = mutableListOf()

        override suspend fun countBrokerSubmittedBetween(
            market: OrderIntentSubmissionMarket,
            from: ZonedDateTime,
            to: ZonedDateTime,
        ): Long {
            countBrokerSubmittedMarkets += market
            countBrokerSubmittedWindows += Pair(from, to)
            return brokerSubmittedCount
        }

        override suspend fun existsActiveDuplicate(
            market: OrderIntentSubmissionMarket,
            strategyExecutionId: String,
            symbol: String,
            side: OrderIntentSubmissionSide,
            orderTag: String,
            from: ZonedDateTime,
            to: ZonedDateTime,
        ): Boolean {
            activeDuplicateMarkets += market
            activeDuplicateWindows += Pair(from, to)
            return duplicateExists
        }

        override suspend fun sumActiveBuyNotional(market: OrderIntentSubmissionMarket): Double {
            activeBuyNotionalMarkets += market
            return activeBuyNotional
        }

        override suspend fun sumActiveSellQuantity(
            symbol: String,
            market: OrderIntentSubmissionMarket,
        ): Long {
            activeSellQuantityRequests += symbol to market
            return activeSellQuantity
        }
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

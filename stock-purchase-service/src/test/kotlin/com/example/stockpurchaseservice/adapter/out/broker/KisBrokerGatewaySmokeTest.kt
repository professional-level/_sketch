package com.example.stockpurchaseservice.adapter.out.broker

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderRejectedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatus
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.StockOrderType
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@Tag("kis-smoke")
class KisBrokerGatewaySmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "KIS_BROKER_SMOKE_ENABLED", matches = "true")
    fun `mock account snapshot and order history smoke`() {
        val config = SmokeConfig.fromEnvironment()
        assumeTrue(!config.submitEnabled, "Run query-only smoke separately before submit smoke to avoid KIS rate limit noise")
        val adapter = brokerGateway(config)

        val snapshot = runBrokerSmokeQueryStep("mock account snapshot", "mock", config) {
            adapter.findAccountSnapshot(
                BrokerAccountSnapshotQuery(
                    market = StockOrderMarket.OVERSEAS_US,
                    exchange = config.exchange,
                    currency = config.currency,
                    isMock = true,
                ),
            )
        }
        val history = runBrokerSmokeQueryStep("mock order history", "mock", config) {
            adapter.findOrderHistory(config.historyQuery(isMock = true))
        }
        val cancelableOrders = runBrokerSmokeQueryStep("mock overseas unfilled orders", "mock", config) {
            adapter.findCancelableOrders(config.cancelableQuery(isMock = true))
        }

        assertEquals(StockOrderMarket.OVERSEAS_US, snapshot.market)
        assertEquals(config.exchange, snapshot.exchange)
        assertEquals(config.currency, snapshot.currency)
        assertTrue(history.all { it.externalOrderId.isNotBlank() })
        assertTrue(cancelableOrders.all { it.orderId.isNotBlank() })
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KIS_BROKER_REAL_QUERY_SMOKE_ENABLED", matches = "true")
    fun `real account snapshot and order history query-only smoke`() {
        val config = SmokeConfig.fromEnvironment()
        assumeTrue(!config.submitEnabled, "Unset KIS_BROKER_SMOKE_SUBMIT_ENABLED for real query-only smoke")
        val adapter = brokerGateway(config)

        val snapshot = runBrokerSmokeQueryStep("real account snapshot", "real", config) {
            adapter.findAccountSnapshot(
                BrokerAccountSnapshotQuery(
                    market = StockOrderMarket.OVERSEAS_US,
                    exchange = config.exchange,
                    currency = config.currency,
                    isMock = false,
                ),
            )
        }
        val history = runBrokerSmokeQueryStep("real order history", "real", config) {
            adapter.findOrderHistory(config.historyQuery(isMock = false))
        }
        val cancelableOrders = runBrokerSmokeQueryStep("real overseas unfilled orders", "real", config) {
            adapter.findCancelableOrders(config.cancelableQuery(isMock = false))
        }

        assertEquals(StockOrderMarket.OVERSEAS_US, snapshot.market)
        assertEquals(config.exchange, snapshot.exchange)
        assertEquals(config.currency, snapshot.currency)
        assertTrue(history.all { it.externalOrderId.isNotBlank() })
        assertTrue(cancelableOrders.all { it.orderId.isNotBlank() })
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KIS_BROKER_DOMESTIC_QUERY_SMOKE_ENABLED", matches = "true")
    fun `mock domestic account snapshot and order history query-only smoke`() {
        val config = SmokeConfig.fromEnvironment()
        assumeTrue(!config.submitEnabled, "Run domestic query-only smoke separately before submit smoke")
        val adapter = brokerGateway(config)

        val snapshot = runBrokerSmokeQueryStep("mock domestic account snapshot", "mock-domestic", config) {
            adapter.findAccountSnapshot(
                BrokerAccountSnapshotQuery(
                    market = StockOrderMarket.DOMESTIC,
                    exchange = config.domesticExchange,
                    currency = config.domesticCurrency,
                    isMock = true,
                ),
            )
        }
        val history = runBrokerSmokeQueryStep("mock domestic order history", "mock-domestic", config) {
            adapter.findOrderHistory(config.domesticHistoryQuery(isMock = true))
        }
        val cancelableOrders = runBrokerSmokeQueryStep("mock domestic cancelable orders", "mock-domestic", config) {
            adapter.findCancelableOrders(config.domesticCancelableQuery(isMock = true))
        }

        assertEquals(StockOrderMarket.DOMESTIC, snapshot.market)
        assertEquals(config.domesticExchange, snapshot.exchange)
        assertEquals(config.domesticCurrency, snapshot.currency)
        assertTrue(history.all { it.externalOrderId.isNotBlank() })
        assertTrue(cancelableOrders.all { it.orderId.isNotBlank() })
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KIS_BROKER_REAL_DOMESTIC_QUERY_SMOKE_ENABLED", matches = "true")
    fun `real domestic account snapshot and order history query-only smoke`() {
        val config = SmokeConfig.fromEnvironment()
        assumeTrue(!config.submitEnabled, "Unset KIS_BROKER_SMOKE_SUBMIT_ENABLED for real domestic query-only smoke")
        val adapter = brokerGateway(config)

        val snapshot = runBrokerSmokeQueryStep("real domestic account snapshot", "real-domestic", config) {
            adapter.findAccountSnapshot(
                BrokerAccountSnapshotQuery(
                    market = StockOrderMarket.DOMESTIC,
                    exchange = config.domesticExchange,
                    currency = config.domesticCurrency,
                    isMock = false,
                ),
            )
        }
        val history = runBrokerSmokeQueryStep("real domestic order history", "real-domestic", config) {
            adapter.findOrderHistory(config.domesticHistoryQuery(isMock = false))
        }
        val cancelableOrders = runBrokerSmokeQueryStep("real domestic cancelable orders", "real-domestic", config) {
            adapter.findCancelableOrders(config.domesticCancelableQuery(isMock = false))
        }

        assertEquals(StockOrderMarket.DOMESTIC, snapshot.market)
        assertEquals(config.domesticExchange, snapshot.exchange)
        assertEquals(config.domesticCurrency, snapshot.currency)
        assertTrue(history.all { it.externalOrderId.isNotBlank() })
        assertTrue(cancelableOrders.all { it.orderId.isNotBlank() })
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KIS_BROKER_SMOKE_ENABLED", matches = "true")
    fun `mock submit query and cancel smoke`() {
        val config = SmokeConfig.fromEnvironment()
        assumeTrue(config.submitEnabled, "Set KIS_BROKER_SMOKE_SUBMIT_ENABLED=true to place a mock order")
        runOverseasSubmitQueryCancelSmoke(config, isMock = true, brokerEnvironment = "mock")
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KIS_BROKER_REAL_SUBMIT_SMOKE_ENABLED", matches = "true")
    fun `real overseas submit query and cancel smoke`() {
        val config = SmokeConfig.fromEnvironment()
        assumeTrue(
            config.liveSubmitConfirmed,
            "Set KIS_BROKER_REAL_SUBMIT_CONFIRM=I_UNDERSTAND_LIVE_ORDER_RISK to place real broker orders",
        )
        runOverseasSubmitQueryCancelSmoke(config, isMock = false, brokerEnvironment = "real")
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KIS_BROKER_DOMESTIC_SUBMIT_SMOKE_ENABLED", matches = "true")
    fun `mock domestic submit query and cancel smoke`() {
        val config = SmokeConfig.fromEnvironment()
        runDomesticSubmitQueryCancelSmoke(config, isMock = true, brokerEnvironment = "mock domestic")
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KIS_BROKER_REAL_DOMESTIC_SUBMIT_SMOKE_ENABLED", matches = "true")
    fun `real domestic submit query and cancel smoke`() {
        val config = SmokeConfig.fromEnvironment()
        assumeTrue(
            config.liveSubmitConfirmed,
            "Set KIS_BROKER_REAL_SUBMIT_CONFIRM=I_UNDERSTAND_LIVE_ORDER_RISK to place real broker orders",
        )
        runDomesticSubmitQueryCancelSmoke(config, isMock = false, brokerEnvironment = "real domestic")
    }

    private fun runOverseasSubmitQueryCancelSmoke(
        config: SmokeConfig,
        isMock: Boolean,
        brokerEnvironment: String,
    ) {
        val adapter = brokerGateway(config)

        val submitCommand = BrokerOrderCommand(
            internalOrderId = UUID.randomUUID(),
            market = StockOrderMarket.OVERSEAS_US,
            side = OrderIntentSide.BUY,
            symbol = config.symbol,
            orderType = StockOrderType.LIMIT,
            price = config.price,
            quantity = config.quantity,
            isMock = isMock,
        )
        val submitted = runBrokerSmokeStep("$brokerEnvironment overseas submit", brokerEnvironment, config, submitCommand) {
            adapter.submitOrder(submitCommand)
        }
        assertTrue(submitted.externalOrderId.isNotBlank())

        val submittedHistory = awaitOrderHistory(adapter, config, submitted.externalOrderId, isMock)
        assertNotNull(
            submittedHistory,
            "Submitted $brokerEnvironment overseas order did not appear in broker history " +
                "after attempts=${config.historyAttempts}, " +
                "pollSeconds=${config.historyPollInterval.seconds}, " +
                "symbol=${config.symbol}, orderId=${submitted.externalOrderId}",
        )
        assertTrue(submittedHistory.remainingQuantity >= config.quantity)

        val cancelCommand = BrokerOrderCancelCommand(
            internalOrderId = UUID.randomUUID(),
            market = StockOrderMarket.OVERSEAS_US,
            symbol = config.symbol,
            originalOrderId = submitted.externalOrderId,
            branchOrderNumber = null,
            orderType = StockOrderType.LIMIT,
            price = config.price,
            quantity = config.quantity,
            cancelAll = true,
            isMock = isMock,
        )
        val cancelled = runBrokerSmokeStep("$brokerEnvironment overseas cancel", brokerEnvironment, config, cancelCommand) {
            adapter.cancelOrder(cancelCommand)
        }

        assertTrue(cancelled.externalOrderId.isNotBlank())

        val cancelledStatus = awaitOrderStatus(
            adapter,
            config,
            submitted.externalOrderId,
            submitted.branchOrderNumber,
            isMock,
        )
        assertEquals(
            BrokerOrderStatus.CANCELLED,
            cancelledStatus.status,
            "Cancelled $brokerEnvironment overseas order did not map to broker CANCELLED status " +
                "after attempts=${config.historyAttempts}, " +
                "pollSeconds=${config.historyPollInterval.seconds}, " +
                "symbol=${config.symbol}, orderId=${submitted.externalOrderId}, latestStatus=$cancelledStatus",
        )
    }

    private fun runDomesticSubmitQueryCancelSmoke(
        config: SmokeConfig,
        isMock: Boolean,
        brokerEnvironment: String,
    ) {
        val adapter = brokerGateway(config)

        val submitCommand = BrokerOrderCommand(
            internalOrderId = UUID.randomUUID(),
            market = StockOrderMarket.DOMESTIC,
            side = OrderIntentSide.BUY,
            symbol = config.domesticSymbol,
            orderType = StockOrderType.LIMIT,
            price = config.domesticPrice,
            quantity = config.domesticQuantity,
            isMock = isMock,
        )
        val submitted = runBrokerSmokeStep("$brokerEnvironment submit", brokerEnvironment, config, submitCommand) {
            adapter.submitOrder(submitCommand)
        }
        assertTrue(submitted.externalOrderId.isNotBlank())
        val branchOrderNumber = submitted.branchOrderNumber
            ?: fail("Domestic $brokerEnvironment order response did not include branch order number: orderId=${submitted.externalOrderId}")
        assertTrue(branchOrderNumber.isNotBlank())

        val submittedHistory = awaitDomesticOrderHistory(adapter, config, submitted.externalOrderId, isMock)
        assertNotNull(
            submittedHistory,
            "Submitted $brokerEnvironment order did not appear in broker history " +
                "after attempts=${config.historyAttempts}, " +
                "pollSeconds=${config.historyPollInterval.seconds}, " +
                "symbol=${config.domesticSymbol}, orderId=${submitted.externalOrderId}, " +
                "branchOrderNumber=$branchOrderNumber",
        )
        assertTrue(submittedHistory.remainingQuantity >= config.domesticQuantity)

        val cancelCommand = BrokerOrderCancelCommand(
            internalOrderId = UUID.randomUUID(),
            market = StockOrderMarket.DOMESTIC,
            symbol = config.domesticSymbol,
            originalOrderId = submitted.externalOrderId,
            branchOrderNumber = branchOrderNumber,
            orderType = StockOrderType.LIMIT,
            price = config.domesticPrice,
            quantity = config.domesticQuantity,
            cancelAll = true,
            isMock = isMock,
        )
        val cancelled = runBrokerSmokeStep("$brokerEnvironment cancel", brokerEnvironment, config, cancelCommand) {
            adapter.cancelOrder(cancelCommand)
        }

        assertTrue(cancelled.externalOrderId.isNotBlank())

        val cancelledStatus = awaitDomesticOrderStatus(
            adapter,
            config,
            submitted.externalOrderId,
            branchOrderNumber,
            isMock,
        )
        assertEquals(
            BrokerOrderStatus.CANCELLED,
            cancelledStatus.status,
            "Cancelled $brokerEnvironment order did not map to broker CANCELLED status " +
                "after attempts=${config.historyAttempts}, " +
                "pollSeconds=${config.historyPollInterval.seconds}, " +
                "symbol=${config.domesticSymbol}, orderId=${submitted.externalOrderId}, " +
                "branchOrderNumber=$branchOrderNumber, latestStatus=$cancelledStatus",
        )
    }

    private fun <T> runBrokerSmokeStep(
        step: String,
        brokerEnvironment: String,
        config: SmokeConfig,
        command: Any,
        block: () -> T,
    ): T {
        return try {
            block()
        } catch (exception: BrokerOrderRejectedException) {
            fail(
                "$step rejected by KIS $brokerEnvironment broker: " +
                    "returnCode=${exception.brokerReturnCode}, " +
                    "messageCode=${exception.brokerMessageCode}, " +
                    "brokerMessage=${exception.brokerMessage}, " +
                    "config=${config.redacted()}, command=$command",
            )
        }
    }

    private fun <T> runBrokerSmokeQueryStep(
        step: String,
        brokerEnvironment: String,
        config: SmokeConfig,
        block: () -> T,
    ): T {
        return try {
            block()
        } catch (exception: RuntimeException) {
            fail(
                "$step failed during KIS $brokerEnvironment broker smoke: " +
                    "exception=${exception::class.java.simpleName}, " +
                    "message=${exception.message}, " +
                    "cause=${exception.cause?.javaClass?.simpleName}, " +
                    "causeMessage=${exception.cause?.message}, " +
                    "config=${config.redacted()}",
            )
        }
    }

    private fun brokerGateway(config: SmokeConfig): KisBrokerGatewayAdapter {
        return KisBrokerGatewayAdapter(
            WebClient.builder()
                .baseUrl(config.baseUrl)
                .build(),
        )
    }

    private fun awaitOrderHistory(
        adapter: KisBrokerGatewayAdapter,
        config: SmokeConfig,
        externalOrderId: String,
        isMock: Boolean,
    ): BrokerOrderHistoryItem? {
        repeat(config.historyAttempts) {
            val match = adapter.findOrderHistory(config.historyQuery(isMock = isMock))
                .firstOrNull { it.externalOrderId == externalOrderId }
            if (match != null) return match
            Thread.sleep(config.historyPollInterval.toMillis())
        }
        return null
    }

    private fun awaitDomesticOrderHistory(
        adapter: KisBrokerGatewayAdapter,
        config: SmokeConfig,
        externalOrderId: String,
        isMock: Boolean,
    ): BrokerOrderHistoryItem? {
        repeat(config.historyAttempts) {
            val match = adapter.findOrderHistory(config.domesticHistoryQuery(isMock = isMock))
                .firstOrNull { it.externalOrderId == externalOrderId }
            if (match != null) return match
            Thread.sleep(config.historyPollInterval.toMillis())
        }
        return null
    }

    private fun awaitOrderStatus(
        adapter: KisBrokerGatewayAdapter,
        config: SmokeConfig,
        externalOrderId: String,
        branchOrderNumber: String?,
        isMock: Boolean,
    ): BrokerOrderStatusDto {
        var latestStatus: BrokerOrderStatusDto? = null
        repeat(config.historyAttempts) {
            latestStatus = adapter.findOrderHistory(config.historyQuery(isMock = isMock)).findStatusFor(
                BrokerOrderStatusQuery(
                    orderIntentId = UUID.randomUUID(),
                    internalOrderId = UUID.randomUUID(),
                    externalOrderId = externalOrderId,
                    branchOrderNumber = branchOrderNumber,
                    symbol = config.symbol,
                    exchange = config.exchange,
                    side = OrderIntentSide.BUY,
                    orderedQuantity = config.quantity.toLong(),
                    submittedPrice = config.price,
                    market = StockOrderMarket.OVERSEAS_US,
                    submittedAt = ZonedDateTime.now(BROKER_ORDER_ZONE),
                ),
            )
            if (latestStatus?.status == BrokerOrderStatus.CANCELLED) return latestStatus!!
            Thread.sleep(config.historyPollInterval.toMillis())
        }
        return latestStatus ?: fail("Broker status polling did not run for orderId=$externalOrderId")
    }

    private fun awaitDomesticOrderStatus(
        adapter: KisBrokerGatewayAdapter,
        config: SmokeConfig,
        externalOrderId: String,
        branchOrderNumber: String?,
        isMock: Boolean,
    ): BrokerOrderStatusDto {
        var latestStatus: BrokerOrderStatusDto? = null
        repeat(config.historyAttempts) {
            latestStatus = adapter.findOrderHistory(config.domesticHistoryQuery(isMock = isMock)).findStatusFor(
                BrokerOrderStatusQuery(
                    orderIntentId = UUID.randomUUID(),
                    internalOrderId = UUID.randomUUID(),
                    externalOrderId = externalOrderId,
                    branchOrderNumber = branchOrderNumber,
                    symbol = config.domesticSymbol,
                    exchange = config.domesticExchange,
                    side = OrderIntentSide.BUY,
                    orderedQuantity = config.domesticQuantity.toLong(),
                    submittedPrice = config.domesticPrice,
                    market = StockOrderMarket.DOMESTIC,
                    submittedAt = ZonedDateTime.now(BROKER_ORDER_ZONE),
                ),
            )
            if (latestStatus?.status == BrokerOrderStatus.CANCELLED) return latestStatus!!
            Thread.sleep(config.historyPollInterval.toMillis())
        }
        return latestStatus ?: fail("Broker status polling did not run for domestic orderId=$externalOrderId")
    }

    private data class SmokeConfig(
        val baseUrl: String,
        val symbol: String,
        val exchange: String,
        val currency: String,
        val domesticSymbol: String,
        val domesticExchange: String,
        val domesticCurrency: String,
        val domesticPrice: Double,
        val domesticQuantity: Int,
        val price: Double,
        val quantity: Int,
        val submitEnabled: Boolean,
        val liveSubmitConfirmation: String,
        val historyAttempts: Int,
        val historyPollInterval: Duration,
    ) {
        val liveSubmitConfirmed: Boolean
            get() = liveSubmitConfirmation == LIVE_SUBMIT_CONFIRMATION

        fun redacted(): String {
            return "SmokeConfig(" +
                "baseUrl=$baseUrl, " +
                "symbol=$symbol, " +
                "exchange=$exchange, " +
                "currency=$currency, " +
                "domesticSymbol=$domesticSymbol, " +
                "domesticExchange=$domesticExchange, " +
                "domesticCurrency=$domesticCurrency, " +
                "domesticPrice=$domesticPrice, " +
                "domesticQuantity=$domesticQuantity, " +
                "price=$price, " +
                "quantity=$quantity, " +
                "submitEnabled=$submitEnabled, " +
                "liveSubmitConfirmed=$liveSubmitConfirmed, " +
                "historyAttempts=$historyAttempts, " +
                "historyPollInterval=$historyPollInterval" +
                ")"
        }

        fun historyQuery(isMock: Boolean): BrokerOrderHistoryQuery {
            val now = ZonedDateTime.now(BROKER_ORDER_ZONE)
            return BrokerOrderHistoryQuery(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = symbol,
                exchange = exchange,
                from = now.minusDays(1),
                to = now.plusDays(1),
                isMock = isMock,
            )
        }

        fun cancelableQuery(isMock: Boolean): BrokerOrderCancelableQuery {
            return BrokerOrderCancelableQuery(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = exchange,
                isMock = isMock,
            )
        }

        fun domesticHistoryQuery(isMock: Boolean): BrokerOrderHistoryQuery {
            val now = ZonedDateTime.now(BROKER_ORDER_ZONE)
            return BrokerOrderHistoryQuery(
                market = StockOrderMarket.DOMESTIC,
                symbol = domesticSymbol,
                exchange = domesticExchange,
                from = now.minusDays(1),
                to = now.plusDays(1),
                isMock = isMock,
            )
        }

        fun domesticCancelableQuery(isMock: Boolean): BrokerOrderCancelableQuery {
            return BrokerOrderCancelableQuery(
                market = StockOrderMarket.DOMESTIC,
                exchange = domesticExchange,
                isMock = isMock,
            )
        }

        companion object {
            fun fromEnvironment(): SmokeConfig {
                return SmokeConfig(
                    baseUrl = setting("KIS_BROKER_SMOKE_BASE_URL", "http://localhost:8079"),
                    symbol = setting("KIS_BROKER_SMOKE_SYMBOL", "TQQQ").uppercase(),
                    exchange = setting("KIS_BROKER_SMOKE_EXCHANGE", "NASD").uppercase(),
                    currency = setting("KIS_BROKER_SMOKE_CURRENCY", "USD").uppercase(),
                    domesticSymbol = setting("KIS_BROKER_SMOKE_DOMESTIC_SYMBOL", "005930"),
                    domesticExchange = setting("KIS_BROKER_SMOKE_DOMESTIC_EXCHANGE", "KRX").uppercase(),
                    domesticCurrency = setting("KIS_BROKER_SMOKE_DOMESTIC_CURRENCY", "KRW").uppercase(),
                    domesticPrice = setting("KIS_BROKER_SMOKE_DOMESTIC_PRICE", "1").toDouble(),
                    domesticQuantity = setting("KIS_BROKER_SMOKE_DOMESTIC_QUANTITY", "1").toInt(),
                    price = setting("KIS_BROKER_SMOKE_PRICE", "1").toDouble(),
                    quantity = setting("KIS_BROKER_SMOKE_QUANTITY", "1").toInt(),
                    submitEnabled = setting("KIS_BROKER_SMOKE_SUBMIT_ENABLED", "false").toBooleanStrictOrNull()
                        ?: false,
                    liveSubmitConfirmation = setting("KIS_BROKER_REAL_SUBMIT_CONFIRM", ""),
                    historyAttempts = setting("KIS_BROKER_SMOKE_HISTORY_ATTEMPTS", "6").toInt(),
                    historyPollInterval = Duration.ofSeconds(
                        setting("KIS_BROKER_SMOKE_HISTORY_POLL_SECONDS", "5").toLong(),
                    ),
                )
            }

            private fun setting(name: String, defaultValue: String): String {
                return System.getenv(name)
                    ?: System.getProperty(name)
                    ?: defaultValue
            }

            private const val LIVE_SUBMIT_CONFIRMATION = "I_UNDERSTAND_LIVE_ORDER_RISK"
        }
    }
}

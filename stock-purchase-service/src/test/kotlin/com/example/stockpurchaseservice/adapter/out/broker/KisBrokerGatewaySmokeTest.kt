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

        assertEquals(StockOrderMarket.OVERSEAS_US, snapshot.market)
        assertEquals(config.exchange, snapshot.exchange)
        assertEquals(config.currency, snapshot.currency)
        assertTrue(history.all { it.externalOrderId.isNotBlank() })
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

        assertEquals(StockOrderMarket.OVERSEAS_US, snapshot.market)
        assertEquals(config.exchange, snapshot.exchange)
        assertEquals(config.currency, snapshot.currency)
        assertTrue(history.all { it.externalOrderId.isNotBlank() })
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

        assertEquals(StockOrderMarket.DOMESTIC, snapshot.market)
        assertEquals(config.domesticExchange, snapshot.exchange)
        assertEquals(config.domesticCurrency, snapshot.currency)
        assertTrue(history.all { it.externalOrderId.isNotBlank() })
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

        assertEquals(StockOrderMarket.DOMESTIC, snapshot.market)
        assertEquals(config.domesticExchange, snapshot.exchange)
        assertEquals(config.domesticCurrency, snapshot.currency)
        assertTrue(history.all { it.externalOrderId.isNotBlank() })
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KIS_BROKER_SMOKE_ENABLED", matches = "true")
    fun `mock submit query and cancel smoke`() {
        val config = SmokeConfig.fromEnvironment()
        assumeTrue(config.submitEnabled, "Set KIS_BROKER_SMOKE_SUBMIT_ENABLED=true to place a mock order")
        val adapter = brokerGateway(config)

        val submitCommand = BrokerOrderCommand(
            internalOrderId = UUID.randomUUID(),
            market = StockOrderMarket.OVERSEAS_US,
            side = OrderIntentSide.BUY,
            symbol = config.symbol,
            orderType = StockOrderType.LIMIT,
            price = config.price,
            quantity = config.quantity,
            isMock = true,
        )
        val submitted = runBrokerSmokeStep("mock submit", config, submitCommand) {
            adapter.submitOrder(submitCommand)
        }
        assertTrue(submitted.externalOrderId.isNotBlank())

        val submittedHistory = awaitOrderHistory(adapter, config, submitted.externalOrderId)
        assertNotNull(
            submittedHistory,
            "Submitted mock order did not appear in broker history " +
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
            isMock = true,
        )
        val cancelled = runBrokerSmokeStep("mock cancel", config, cancelCommand) {
            adapter.cancelOrder(cancelCommand)
        }

        assertTrue(cancelled.externalOrderId.isNotBlank())

        val cancelledStatus = awaitOrderStatus(adapter, config, submitted.externalOrderId, submitted.branchOrderNumber)
        assertEquals(
            BrokerOrderStatus.CANCELLED,
            cancelledStatus.status,
            "Cancelled mock order did not map to broker CANCELLED status " +
                "after attempts=${config.historyAttempts}, " +
                "pollSeconds=${config.historyPollInterval.seconds}, " +
                "symbol=${config.symbol}, orderId=${submitted.externalOrderId}, latestStatus=$cancelledStatus",
        )
    }

    private fun <T> runBrokerSmokeStep(
        step: String,
        config: SmokeConfig,
        command: Any,
        block: () -> T,
    ): T {
        return try {
            block()
        } catch (exception: BrokerOrderRejectedException) {
            fail(
                "$step rejected by KIS mock broker: " +
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
    ): BrokerOrderHistoryItem? {
        repeat(config.historyAttempts) {
            val match = adapter.findOrderHistory(config.historyQuery(isMock = true))
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
    ): BrokerOrderStatusDto {
        var latestStatus: BrokerOrderStatusDto? = null
        repeat(config.historyAttempts) {
            latestStatus = adapter.findOrderHistory(config.historyQuery(isMock = true)).findStatusFor(
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

    private data class SmokeConfig(
        val baseUrl: String,
        val symbol: String,
        val exchange: String,
        val currency: String,
        val domesticSymbol: String,
        val domesticExchange: String,
        val domesticCurrency: String,
        val price: Double,
        val quantity: Int,
        val submitEnabled: Boolean,
        val historyAttempts: Int,
        val historyPollInterval: Duration,
    ) {
        fun redacted(): String {
            return "SmokeConfig(" +
                "baseUrl=$baseUrl, " +
                "symbol=$symbol, " +
                "exchange=$exchange, " +
                "currency=$currency, " +
                "domesticSymbol=$domesticSymbol, " +
                "domesticExchange=$domesticExchange, " +
                "domesticCurrency=$domesticCurrency, " +
                "price=$price, " +
                "quantity=$quantity, " +
                "submitEnabled=$submitEnabled, " +
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
                    price = setting("KIS_BROKER_SMOKE_PRICE", "1").toDouble(),
                    quantity = setting("KIS_BROKER_SMOKE_QUANTITY", "1").toInt(),
                    submitEnabled = setting("KIS_BROKER_SMOKE_SUBMIT_ENABLED", "false").toBooleanStrictOrNull()
                        ?: false,
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
        }
    }
}

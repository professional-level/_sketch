package com.example.stockpurchaseservice.adapter.out.broker

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
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

@Tag("kis-smoke")
@EnabledIfEnvironmentVariable(named = "KIS_BROKER_SMOKE_ENABLED", matches = "true")
class KisBrokerGatewaySmokeTest {

    @Test
    fun `mock account snapshot and order history smoke`() {
        val config = SmokeConfig.fromEnvironment()
        val adapter = brokerGateway(config)

        val snapshot = adapter.findAccountSnapshot(
            BrokerAccountSnapshotQuery(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = config.exchange,
                currency = config.currency,
                isMock = true,
            ),
        )
        val history = adapter.findOrderHistory(config.historyQuery())

        assertEquals(StockOrderMarket.OVERSEAS_US, snapshot.market)
        assertEquals(config.exchange, snapshot.exchange)
        assertEquals(config.currency, snapshot.currency)
        assertTrue(history.all { it.externalOrderId.isNotBlank() })
    }

    @Test
    fun `mock submit query and cancel smoke`() {
        val config = SmokeConfig.fromEnvironment()
        assumeTrue(config.submitEnabled, "Set KIS_BROKER_SMOKE_SUBMIT_ENABLED=true to place a mock order")
        val adapter = brokerGateway(config)

        val submitted = adapter.submitOrder(
            BrokerOrderCommand(
                internalOrderId = UUID.randomUUID(),
                market = StockOrderMarket.OVERSEAS_US,
                side = OrderIntentSide.BUY,
                symbol = config.symbol,
                orderType = StockOrderType.LIMIT,
                price = config.price,
                quantity = config.quantity,
                isMock = true,
            ),
        )
        assertTrue(submitted.externalOrderId.isNotBlank())

        val submittedHistory = awaitOrderHistory(adapter, config, submitted.externalOrderId)
        assertNotNull(submittedHistory, "Submitted mock order did not appear in broker history")
        assertTrue(submittedHistory.remainingQuantity >= config.quantity)

        val cancelled = adapter.cancelOrder(
            BrokerOrderCancelCommand(
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
            ),
        )

        assertTrue(cancelled.externalOrderId.isNotBlank())
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
            val match = adapter.findOrderHistory(config.historyQuery())
                .firstOrNull { it.externalOrderId == externalOrderId }
            if (match != null) return match
            Thread.sleep(config.historyPollInterval.toMillis())
        }
        return null
    }

    private data class SmokeConfig(
        val baseUrl: String,
        val symbol: String,
        val exchange: String,
        val currency: String,
        val price: Double,
        val quantity: Int,
        val submitEnabled: Boolean,
        val historyAttempts: Int,
        val historyPollInterval: Duration,
    ) {
        fun historyQuery(): BrokerOrderHistoryQuery {
            val now = ZonedDateTime.now(BROKER_ORDER_ZONE)
            return BrokerOrderHistoryQuery(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = symbol,
                from = now.minusDays(1),
                to = now.plusDays(1),
                isMock = true,
            )
        }

        companion object {
            fun fromEnvironment(): SmokeConfig {
                return SmokeConfig(
                    baseUrl = setting("KIS_BROKER_SMOKE_BASE_URL", "http://localhost:8079"),
                    symbol = setting("KIS_BROKER_SMOKE_SYMBOL", "TQQQ").uppercase(),
                    exchange = setting("KIS_BROKER_SMOKE_EXCHANGE", "NASD").uppercase(),
                    currency = setting("KIS_BROKER_SMOKE_CURRENCY", "USD").uppercase(),
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

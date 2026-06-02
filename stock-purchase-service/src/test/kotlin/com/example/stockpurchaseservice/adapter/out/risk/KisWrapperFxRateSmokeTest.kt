package com.example.stockpurchaseservice.adapter.out.risk

import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@Tag("kis-smoke")
@EnabledIfEnvironmentVariable(named = "KIS_FX_SMOKE_ENABLED", matches = "true")
class KisWrapperFxRateSmokeTest {

    @Test
    fun `kis wrapper fx rate smoke`() {
        val config = SmokeConfig.fromEnvironment()
        val adapter = KisWrapperFxRateAdapter(
            webClient = WebClient.builder()
                .baseUrl(config.baseUrl)
                .build(),
            properties = config.toCurrencyConversionProperties(),
        )

        val quote = try {
            adapter.getRateToBase(config.sourceCurrency, config.baseCurrency)
        } catch (exception: RuntimeException) {
            fail(
                "KIS wrapper FX smoke failed: " +
                    "exception=${exception::class.java.simpleName}, " +
                    "message=${exception.message}, " +
                    "cause=${exception.cause?.javaClass?.simpleName}, " +
                    "causeMessage=${exception.cause?.message}, " +
                    "config=${config.redacted()}",
            )
        }

        assertNotNull(quote, "KIS wrapper FX smoke returned no quote: config=${config.redacted()}")
        assertEquals(config.sourceCurrency, quote.sourceCurrency)
        assertEquals(config.baseCurrency, quote.baseCurrency)
        assertTrue(quote.rateToBase > 0.0)
    }

    private data class SmokeConfig(
        val baseUrl: String,
        val sourceCurrency: String,
        val baseCurrency: String,
        val marketDivCode: String,
        val symbol: String,
        val invert: Boolean,
        val isMock: Boolean,
        val fromDate: String,
        val toDate: String,
        val periodDivCode: String,
        val timeout: Duration,
    ) {
        fun toCurrencyConversionProperties(): OrderRiskProperties.CurrencyConversionProperties {
            return OrderRiskProperties.CurrencyConversionProperties().apply {
                kisWrapper.timeout = timeout
                kisWrapper.defaultMarketDivCode = marketDivCode
                kisWrapper.pairs["$sourceCurrency-$baseCurrency"] =
                    OrderRiskProperties.KisWrapperFxRatePairProperties().apply {
                        marketDivCode = this@SmokeConfig.marketDivCode
                        symbol = this@SmokeConfig.symbol
                        invert = this@SmokeConfig.invert
                        isMock = this@SmokeConfig.isMock
                        fromDate = this@SmokeConfig.fromDate
                        toDate = this@SmokeConfig.toDate
                        periodDivCode = this@SmokeConfig.periodDivCode
                    }
            }
        }

        fun redacted(): String {
            return "SmokeConfig(" +
                "baseUrl=$baseUrl, " +
                "sourceCurrency=$sourceCurrency, " +
                "baseCurrency=$baseCurrency, " +
                "marketDivCode=$marketDivCode, " +
                "symbol=$symbol, " +
                "invert=$invert, " +
                "isMock=$isMock, " +
                "fromDate=$fromDate, " +
                "toDate=$toDate, " +
                "periodDivCode=$periodDivCode, " +
                "timeout=$timeout" +
                ")"
        }

        companion object {
            fun fromEnvironment(): SmokeConfig {
                return SmokeConfig(
                    baseUrl = setting("KIS_FX_SMOKE_BASE_URL", "http://localhost:8079"),
                    sourceCurrency = setting("KIS_FX_SMOKE_SOURCE_CURRENCY", "KRW").uppercase(),
                    baseCurrency = setting("KIS_FX_SMOKE_BASE_CURRENCY", "USD").uppercase(),
                    marketDivCode = setting("KIS_FX_SMOKE_MARKET_DIV_CODE", "KX").uppercase(),
                    symbol = setting("KIS_FX_SMOKE_SYMBOL", "USDKRW").uppercase(),
                    invert = setting("KIS_FX_SMOKE_INVERT", "true").toBooleanStrictOrNull() ?: true,
                    isMock = setting("KIS_FX_SMOKE_IS_MOCK", "true").toBooleanStrictOrNull() ?: true,
                    fromDate = setting("KIS_FX_SMOKE_FROM_DATE", ""),
                    toDate = setting("KIS_FX_SMOKE_TO_DATE", ""),
                    periodDivCode = setting("KIS_FX_SMOKE_PERIOD_DIV_CODE", "D").uppercase(),
                    timeout = Duration.ofSeconds(setting("KIS_FX_SMOKE_TIMEOUT_SECONDS", "10").toLong()),
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

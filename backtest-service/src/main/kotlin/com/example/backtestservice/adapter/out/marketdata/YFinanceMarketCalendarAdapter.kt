package com.example.backtestservice.adapter.out.marketdata

import com.example.backtestservice.application.port.out.MarketCalendarPort
import com.example.backtestservice.application.port.out.MarketCalendarSource
import com.example.backtestservice.application.port.out.MarketTradingDaysQuery
import com.example.backtestservice.application.port.out.MarketTradingDaysResult
import com.example.common.ExternalApiAdapter
import com.example.common.market.UsEquityMarketCalendar
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.LocalDate

@ExternalApiAdapter
class YFinanceMarketCalendarAdapter(
    @Value("\${akra.backtest.market-data.yfinance.base-url:http://localhost:8095}")
    private val baseUrl: String,
    @Value("\${akra.backtest.market-data.yfinance.timeout-seconds:30}")
    private val defaultTimeoutSeconds: Long,
    @Value("\${akra.backtest.market-data.yfinance.max-in-memory-bytes:16777216}")
    private val maxInMemoryBytes: Int,
) : MarketCalendarPort {
    init {
        require(maxInMemoryBytes > 0) { "maxInMemoryBytes must be positive" }
    }

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(baseUrl)
        .exchangeStrategies(
            ExchangeStrategies.builder()
                .codecs { codecs -> codecs.defaultCodecs().maxInMemorySize(maxInMemoryBytes) }
                .build(),
        )
        .build()

    override fun findTradingDays(query: MarketTradingDaysQuery): MarketTradingDaysResult {
        query.validate()
        return runCatching { fetchPandasTradingDays(query) }
            .getOrElse { exception ->
                logger.warn(
                    "falling back to built-in US equity market calendar: market={}, from={}, to={}",
                    query.market,
                    query.from,
                    query.to,
                    exception,
                )
                fallbackTradingDays(query)
            }
    }

    private fun fetchPandasTradingDays(query: MarketTradingDaysQuery): MarketTradingDaysResult {
        val response = webClient.post()
            .uri("/market-calendar/valid-days")
            .bodyValue(query.toRequest())
            .retrieve()
            .bodyToMono(YFinanceValidTradingDaysResponse::class.java)
            .block(Duration.ofSeconds(defaultTimeoutSeconds.coerceAtLeast(1)))
            ?: throw IllegalStateException("yfinance sidecar returned no market calendar response")

        return MarketTradingDaysResult(
            market = response.market.trim().uppercase(),
            dates = response.validDays.sorted(),
            source = MarketCalendarSource.PANDAS_MARKET_CALENDARS,
        )
    }

    private fun fallbackTradingDays(query: MarketTradingDaysQuery): MarketTradingDaysResult {
        val market = query.market.trim().uppercase()
        require(market == US_MARKET) {
            "pandas market calendar failed and no fallback is available for market=${query.market}"
        }
        return MarketTradingDaysResult(
            market = market,
            dates = generateSequence(query.from) { date ->
                date.plusDays(1).takeIf { !it.isAfter(query.to) }
            }
                .filter(UsEquityMarketCalendar::isTradingDay)
                .toList(),
            source = MarketCalendarSource.US_EQUITY_MARKET_CALENDAR_FALLBACK,
        )
    }

    private fun MarketTradingDaysQuery.validate() {
        require(market.isNotBlank()) { "market is required" }
        require(!from.isAfter(to)) { "from must be on or before to" }
    }

    private fun MarketTradingDaysQuery.toRequest(): YFinanceValidTradingDaysRequest {
        return YFinanceValidTradingDaysRequest(
            market = market.trim().uppercase(),
            start = from.toString(),
            end = to.toString(),
        )
    }

    companion object {
        private const val US_MARKET = "US"
        private val logger = LoggerFactory.getLogger(YFinanceMarketCalendarAdapter::class.java)
    }
}

data class YFinanceValidTradingDaysRequest(
    val market: String,
    val start: String,
    val end: String,
)

data class YFinanceValidTradingDaysResponse(
    val market: String,
    val calendar: String,
    val start: LocalDate,
    val end: LocalDate,
    @JsonProperty("valid_days")
    val validDays: List<LocalDate> = emptyList(),
)

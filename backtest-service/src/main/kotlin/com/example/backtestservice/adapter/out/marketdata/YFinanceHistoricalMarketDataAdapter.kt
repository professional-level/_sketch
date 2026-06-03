package com.example.backtestservice.adapter.out.marketdata

import com.example.backtestservice.application.port.out.ExternalHistoricalDailyCandlesQuery
import com.example.backtestservice.application.port.out.ExternalHistoricalMarketDataPort
import com.example.backtestservice.domain.market.HistoricalCandle
import com.example.common.ExternalApiAdapter
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate

@ExternalApiAdapter
class YFinanceHistoricalMarketDataAdapter(
    @Value("\${akra.backtest.market-data.yfinance.base-url:http://localhost:8095}")
    private val baseUrl: String,
    @Value("\${akra.backtest.market-data.yfinance.timeout-seconds:30}")
    private val defaultTimeoutSeconds: Long,
) : ExternalHistoricalMarketDataPort {
    private val webClient: WebClient = WebClient.builder()
        .baseUrl(baseUrl)
        .build()

    override fun fetchDailyCandles(query: ExternalHistoricalDailyCandlesQuery): List<HistoricalCandle> {
        val response = webClient.post()
            .uri("/daily-candles")
            .bodyValue(query.toRequest())
            .retrieve()
            .bodyToMono(YFinanceDailyCandlesResponse::class.java)
            .block(Duration.ofSeconds(query.timeoutSeconds.coerceAtLeast(1)))
            ?: throw IllegalStateException("yfinance sidecar returned no response")
        val symbol = query.symbol.trim().uppercase()
        return response.candles[symbol]
            .orEmpty()
            .map { it.toHistoricalCandle(symbol, query.market) }
            .filter { it.date >= query.from && it.date <= query.to }
            .sortedBy { it.date }
    }

    private fun ExternalHistoricalDailyCandlesQuery.toRequest(): YFinanceDailyCandlesRequest {
        return YFinanceDailyCandlesRequest(
            tickers = listOf(symbol.trim().uppercase()),
            start = from.toString(),
            end = to.plusDays(1).toString(),
            autoAdjust = autoAdjust,
            timeout = timeoutSeconds.takeIf { it > 0 }?.toDouble() ?: defaultTimeoutSeconds.toDouble(),
        )
    }
}

data class YFinanceDailyCandlesRequest(
    val tickers: List<String>,
    val start: String,
    val end: String,
    val autoAdjust: Boolean = false,
    val timeout: Double = 30.0,
)

data class YFinanceDailyCandlesResponse(
    val candles: Map<String, List<YFinanceDailyCandleResponse>> = emptyMap(),
)

data class YFinanceDailyCandleResponse(
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    @JsonProperty("adj_close")
    val adjustedClose: BigDecimal,
    val dividend: String? = null,
    val volume: Long,
) {
    fun toHistoricalCandle(symbol: String, market: String): HistoricalCandle {
        return HistoricalCandle(
            symbol = symbol,
            market = market.trim().uppercase(),
            date = date,
            open = open,
            high = high,
            low = low,
            close = close,
            adjustedClose = adjustedClose,
            dividend = dividend.toBigDecimalOrZero(),
            volume = volume,
            source = "YFINANCE",
        )
    }

    private fun String?.toBigDecimalOrZero(): BigDecimal {
        val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return BigDecimal.ZERO
        return if (value.equals("nan", ignoreCase = true)) BigDecimal.ZERO else value.toBigDecimal()
    }
}

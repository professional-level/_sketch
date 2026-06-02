package com.example.stockpurchaseservice.adapter.out.risk

import com.example.stockpurchaseservice.adapter.out.api.ExternalApiCallOptions
import com.example.stockpurchaseservice.adapter.out.api.getExternalApi
import com.example.stockpurchaseservice.application.port.out.FxRatePort
import com.example.stockpurchaseservice.application.port.out.FxRateQuoteDto
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.web.reactive.function.client.WebClient
import java.time.ZonedDateTime

internal class HttpFxRateAdapter(
    private val webClient: WebClient,
    private val properties: OrderRiskProperties.CurrencyConversionProperties,
) : FxRatePort {

    override fun getRateToBase(sourceCurrency: String, baseCurrency: String): FxRateQuoteDto? {
        val source = sourceCurrency.normalizedCurrency()
        val base = baseCurrency.normalizedCurrency()
        if (source.isBlank() || base.isBlank()) return null
        if (source == base) {
            return FxRateQuoteDto(
                sourceCurrency = source,
                baseCurrency = base,
                rateToBase = 1.0,
                provider = PROVIDER,
            )
        }

        val response = webClient.getExternalApi(
            uri = properties.http.path,
            queryParameters = mapOf(
                properties.http.sourceCurrencyParam to source,
                properties.http.baseCurrencyParam to base,
            ),
            responseType = JsonNode::class.java,
            callOptions = ExternalApiCallOptions(timeout = properties.http.timeout),
        )
        val rate = response.textOrNull("rateToBase", "rate", "exchangeRate", "RATE")
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?: return null
        return FxRateQuoteDto(
            sourceCurrency = source,
            baseCurrency = base,
            rateToBase = rate,
            provider = response.textOrNull("provider").orEmpty().ifBlank { PROVIDER },
            observedAt = response.textOrNull("observedAt", "timestamp", "asOf")?.let {
                runCatching { ZonedDateTime.parse(it) }.getOrNull()
            },
        )
    }

    private fun JsonNode.textOrNull(vararg fieldNames: String): String? {
        return fieldNames
            .asSequence()
            .map { path(it).asText("").trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun String.normalizedCurrency(): String {
        return trim().uppercase()
    }

    companion object {
        private const val PROVIDER = "http"
    }
}

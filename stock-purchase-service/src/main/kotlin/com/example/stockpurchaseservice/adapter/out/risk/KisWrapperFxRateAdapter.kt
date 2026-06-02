package com.example.stockpurchaseservice.adapter.out.risk

import com.example.stockpurchaseservice.adapter.out.api.ExternalApiCallOptions
import com.example.stockpurchaseservice.adapter.out.api.getExternalApi
import com.example.stockpurchaseservice.application.port.out.FxRatePort
import com.example.stockpurchaseservice.application.port.out.FxRateQuoteDto
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate
import java.time.ZoneId

internal class KisWrapperFxRateAdapter(
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

        val pair = properties.kisWrapper.findPair(source, base) ?: return null
        val response = webClient.getExternalApi(
            uri = properties.kisWrapper.path,
            queryParameters = pair.toQueryParameters(properties.kisWrapper.defaultMarketDivCode),
            responseType = JsonNode::class.java,
            callOptions = ExternalApiCallOptions(timeout = properties.kisWrapper.timeout),
        )
        val rawRate = response.textOrNull("rate", "rateToBase", "exchangeRate")
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?: throw response.toMissingRateException()
        val rate = if (pair.invert) 1.0 / rawRate else rawRate
        return FxRateQuoteDto(
            sourceCurrency = source,
            baseCurrency = base,
            rateToBase = rate,
            provider = response.textOrNull("provider").orEmpty().ifBlank { PROVIDER },
            observedAt = response.textOrNull("observedAt")?.let {
                runCatching { java.time.ZonedDateTime.parse(it) }.getOrNull()
            } ?: response.textOrNull("observedDate")?.let {
                runCatching {
                    LocalDate.parse(it, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                        .atStartOfDay(ZoneId.of("Asia/Seoul"))
                }.getOrNull()
            },
        )
    }

    private fun OrderRiskProperties.KisWrapperFxRateProperties.findPair(
        sourceCurrency: String,
        baseCurrency: String,
    ): OrderRiskProperties.KisWrapperFxRatePairProperties? {
        val keys = listOf(
            "$sourceCurrency-$baseCurrency",
            "$sourceCurrency:$baseCurrency",
            "$sourceCurrency/$baseCurrency",
            "$sourceCurrency$baseCurrency",
        )
        return keys.asSequence()
            .mapNotNull { key -> pairs[key] ?: pairs[key.lowercase()] ?: pairs[key.uppercase()] }
            .firstOrNull { it.symbol.isNotBlank() }
    }

    private fun OrderRiskProperties.KisWrapperFxRatePairProperties.toQueryParameters(
        defaultMarketDivCode: String,
    ): Map<String, String> {
        return buildMap {
            put("marketDivCode", marketDivCode.ifBlank { defaultMarketDivCode }.uppercase())
            put("symbol", symbol.uppercase())
            put("isMock", isMock.toString())
            put("periodDivCode", periodDivCode.ifBlank { "D" }.uppercase())
            if (fromDate.isNotBlank()) put("fromDate", fromDate)
            if (toDate.isNotBlank()) put("toDate", toDate)
        }
    }

    private fun JsonNode.textOrNull(vararg fieldNames: String): String? {
        return fieldNames
            .asSequence()
            .map { path(it).asText("").trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun JsonNode.toMissingRateException(): IllegalStateException {
        val diagnostic = path("diagnostic")
        if (!diagnostic.isObject) {
            return IllegalStateException("KIS wrapper FX response has no positive rate")
        }
        return IllegalStateException(
            "KIS wrapper FX response has no positive rate: " +
                "returnCode=${diagnostic.textOrNull("returnCode")}, " +
                "messageCode=${diagnostic.textOrNull("messageCode")}, " +
                "message=${diagnostic.textOrNull("message")}, " +
                "output1Fields=${diagnostic.path("output1Fields").textList()}, " +
                "output2Fields=${diagnostic.path("output2Fields").textList()}",
        )
    }

    private fun JsonNode.textList(): List<String> {
        return when {
            isArray -> elements().asSequence().map { it.asText() }.filter { it.isNotBlank() }.toList()
            else -> emptyList()
        }
    }

    private fun String.normalizedCurrency(): String {
        return trim().uppercase()
    }

    companion object {
        private const val PROVIDER = "kis-wrapper"
    }
}

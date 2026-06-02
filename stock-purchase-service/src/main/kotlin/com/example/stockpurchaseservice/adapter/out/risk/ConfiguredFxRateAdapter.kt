package com.example.stockpurchaseservice.adapter.out.risk

import com.example.stockpurchaseservice.application.port.out.FxRatePort
import com.example.stockpurchaseservice.application.port.out.FxRateQuoteDto
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties

internal class ConfiguredFxRateAdapter(
    private val properties: OrderRiskProperties,
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
        val configuredBase = properties.currencyConversion.baseCurrency.normalizedCurrency()
        if (base != configuredBase) return null
        val rate = properties.currencyConversion.ratesToBase[source]
            ?: properties.currencyConversion.ratesToBase[source.lowercase()]
            ?: properties.currencyConversion.ratesToBase[source.uppercase()]
            ?: return null
        if (rate <= 0.0) return null
        return FxRateQuoteDto(
            sourceCurrency = source,
            baseCurrency = base,
            rateToBase = rate,
            provider = PROVIDER,
        )
    }

    private fun String.normalizedCurrency(): String {
        return trim().uppercase()
    }

    companion object {
        private const val PROVIDER = "configured"
    }
}

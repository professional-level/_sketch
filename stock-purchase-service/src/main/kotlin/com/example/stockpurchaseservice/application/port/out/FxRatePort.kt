package com.example.stockpurchaseservice.application.port.out

import java.time.ZonedDateTime

interface FxRatePort {
    fun getRateToBase(sourceCurrency: String, baseCurrency: String): FxRateQuoteDto?
}

data class FxRateQuoteDto(
    val sourceCurrency: String,
    val baseCurrency: String,
    val rateToBase: Double,
    val provider: String,
    val observedAt: ZonedDateTime? = null,
) {
    init {
        require(sourceCurrency.isNotBlank()) { "sourceCurrency must not be blank" }
        require(baseCurrency.isNotBlank()) { "baseCurrency must not be blank" }
        require(rateToBase > 0.0) { "rateToBase must be positive" }
        require(provider.isNotBlank()) { "provider must not be blank" }
    }
}

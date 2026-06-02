package com.example.stockpurchaseservice.adapter.out.api

import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket

internal fun ExecutedStockDto.withMarketQualifiedExecutionId(market: StockOrderMarket): ExecutedStockDto {
    return copy(externalExecutionId = externalExecutionId.marketQualified(market))
}

internal fun BrokerOrderStatusDto.withMarketQualifiedExecutionId(market: StockOrderMarket): BrokerOrderStatusDto {
    val executionId = externalExecutionId ?: return this
    return copy(externalExecutionId = executionId.marketQualified(market))
}

private fun String.marketQualified(market: StockOrderMarket): String {
    val prefix = "${market.name}:"
    return if (startsWith(prefix)) this else "$prefix$this"
}

package com.example.stocksearchservice.domain.strategy

import com.example.stocksearchservice.domain.Stock

class FinalPriceBatingStrategyV1 private constructor(
    val stock: Stock,
) {
    fun isValidCurrentValue(): Boolean {
        return validCurrentStockDerivative() && validCurrentStockVolume()
    }

    private fun validCurrentStockDerivative(): Boolean {
        return stock.stockDerivative.value >= 0
    }

    private fun validCurrentStockVolume(): Boolean {
        return stock.stockVolume.value >= 30000 // TODO: 정확한 값 체크 필요
    }

    companion object {
        private fun of(stock: Stock): FinalPriceBatingStrategyV1 = FinalPriceBatingStrategyV1(stock)
        fun validListOf(stocks: List<Stock>): List<FinalPriceBatingStrategyV1> {
            return stocks.map { of(it) }.filter { it.isValidCurrentValue() }
        }

        fun validOf(stock: Stock): FinalPriceBatingStrategyV1? = validListOf(listOf(stock)).firstOrNull()
    }
}

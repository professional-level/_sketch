package com.example.stocksearchservice.domain.strategy

import com.example.stocksearchservice.domain.Stock

class FinalPriceBatingStrategyV1 private constructor(
    val stock: Stock,
    val rank: Int,
) {
    constructor(stock: Stock, rank: Int, foreignerStockVolume: ForeignerStockVolume) : this(
        stock = stock,
        rank = rank,
    ) {
        this.foreignerStockVolume = foreignerStockVolume
    }

    // TODO: 훨씬 좋은 구조가 있을 것 같다
    lateinit var foreignerStockVolume: ForeignerStockVolume

    data class ForeignerStockVolume(val value: Long)

    fun setForeignerStockVolume(volume: Long) {
        foreignerStockVolume = ForeignerStockVolume(volume)
    }

    fun isValidCurrentValue(): Boolean {
        return validCurrentStockDerivative() && validCurrentStockVolume()
    }

    fun isValidProgramForeignerTradeVolume(): Boolean {
        // TODO: 비율에 대한 조정 필요
        return foreignerStockVolume.value >= (stock.stockTotalVolume.value) * 0.3
    }

    private fun validCurrentStockDerivative(): Boolean {
        return stock.stockDerivative.value >= 0
    }

    private fun validCurrentStockVolume(): Boolean {
        return stock.stockVolume.value >= 30000 // TODO: 정확한 값 체크 필요
    }

    companion object {
        private fun of(stock: Stock, rank: Int): FinalPriceBatingStrategyV1 = FinalPriceBatingStrategyV1(stock, rank)
        fun validListOf(stocks: List<Stock>): List<FinalPriceBatingStrategyV1> {
            return stocks.mapIndexed { rank, stock -> of(stock, rank + 1) }.filter { it.isValidCurrentValue() }
        }

        // TODO: 단건 validation의 경우에는 순위에 대한 보장이 필요하다
        fun validOf(stock: Stock): FinalPriceBatingStrategyV1? = validListOf(listOf(stock)).firstOrNull()
    }
}

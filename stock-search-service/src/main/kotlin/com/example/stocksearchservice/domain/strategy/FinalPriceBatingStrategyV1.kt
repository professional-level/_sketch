package com.example.stocksearchservice.domain.strategy

import com.example.common.domain.event.DomainEvent
import com.example.common.domain.event.EventSupportedEntity
import com.example.stocksearchservice.domain.Stock
import com.example.stocksearchservice.domain.event.StrategyCreatedEvent
import com.example.stocksearchservice.domain.event.StrategyType
import java.time.ZonedDateTime

interface StockStrategy : EventSupportedEntity

class FinalPriceBatingStrategyV1 private constructor(
    val stock: Stock,
    val rank: Int,
) : StockStrategy {
    constructor(stock: Stock, rank: Int, foreignerStockVolume: ForeignerStockVolume) : this(
        stock = stock,
        rank = rank,
    ) {
        this.foreignerStockVolume = foreignerStockVolume
    }

    lateinit var foreignerStockVolume: ForeignerStockVolume

    data class ForeignerStockVolume(val value: Long)

    fun setForeignerStockVolume(volume: Long) {
        foreignerStockVolume = ForeignerStockVolume(volume)
    }

    fun isValidCurrentValue(): Boolean {
        return validCurrentStockDerivative() && validCurrentStockVolume()
    }

    fun isValidProgramForeignerTradeVolume(): Boolean {
        return foreignerStockVolume.value >= ((stock.stockTotalVolume.value / 100) * 0.3).toLong()
    }

    private fun validCurrentStockDerivative(): Boolean {
        return stock.stockDerivative.value >= 0
    }

    private fun validCurrentStockVolume(): Boolean {
        return stock.stockVolume.value >= 30000
    }

    companion object {
        private const val STRATEGY_VERSION = "v1"
        private const val DEFAULT_BUDGET = 1_000_000.0
        private const val QUANTITY_POLICY = "BUDGET_DIVIDED_BY_TARGET_BUY_PRICE"

        fun default() = FinalPriceBatingStrategyV1(Stock.default(), 0) // Debug용 함수
        
        fun of(stock: Stock, rank: Int): FinalPriceBatingStrategyV1 = FinalPriceBatingStrategyV1(stock, rank)
    }

    override val events: MutableList<DomainEvent> = mutableListOf()

    override fun complete() {
        events.add(
            StrategyCreatedEvent(
                stockId = this.stock.stockId.value,
                stockName = this.stock.stockName.value,
                savedAt = ZonedDateTime.now(), // TODO: event 발행 시점에 넣는 것이 아니라 객체가 이미 갖고 있도록.
                type = StrategyType.FinalPriceBatingV1,
                strategyId = "FinalPriceBatingV1:${this.stock.stockId.value}",
                strategyVersion = STRATEGY_VERSION,
                decisionPrice = this.stock.stockPrice.value.toDouble(),
                targetBuyPrice = this.stock.stockPrice.value.toDouble(),
                budget = DEFAULT_BUDGET,
                quantityPolicy = QUANTITY_POLICY,
            ),
        )
    }

    override fun project(domainEvent: DomainEvent) = Unit
}

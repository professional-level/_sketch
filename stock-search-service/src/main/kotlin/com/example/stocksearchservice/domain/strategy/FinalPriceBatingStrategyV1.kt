package com.example.stocksearchservice.domain.strategy

import com.example.common.domain.event.DomainEvent
import com.example.common.domain.event.EventSupportedEntity
import com.example.stocksearchservice.domain.Stock
import com.example.stocksearchservice.domain.event.StrategyCreatedEvent
import com.example.stocksearchservice.domain.event.StrategyType
import java.time.ZonedDateTime

// TODO: 적확한 위치 필요, Strategy Entity를 묶기 위한 interface. 추가적인 정보를 담아볼까
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

    // TODO: 훨씬 좋은 구조가 있을 것 같다
    lateinit var foreignerStockVolume: ForeignerStockVolume

    init {
        /* TODO: 초기화 시점에, 조건을 충족하는 객체만 반환 할 수 있도록 하는 방법이 있을까 */
        // this.isValidCurrentValue()
    }

    data class ForeignerStockVolume(val value: Long)

    fun setForeignerStockVolume(volume: Long) {
        foreignerStockVolume = ForeignerStockVolume(volume)
    }

    fun isValidCurrentValue(): Boolean {
        return validCurrentStockDerivative() && validCurrentStockVolume()
    }

    fun isValidProgramForeignerTradeVolume(): Boolean {
        // TODO: 비율에 대한 조정 필요
        // stockTotalVolume / 100000000
        // foreignerStockVolume.value / 1000000
        // stockTotalVolume / 100, foreignerStockVolume.value
        return foreignerStockVolume.value >= ((stock.stockTotalVolume.value / 100) * 0.3).toLong()
    }

    private fun validCurrentStockDerivative(): Boolean {
        return stock.stockDerivative.value >= 0
    }

    private fun validCurrentStockVolume(): Boolean {
        return stock.stockVolume.value >= 30000 // TODO: 정확한 값 체크 필요
    }

    companion object {
        fun default() = FinalPriceBatingStrategyV1(Stock.default(), 0) // Debug용 함수
        private fun of(stock: Stock, rank: Int): FinalPriceBatingStrategyV1 = FinalPriceBatingStrategyV1(stock, rank)
        fun validListOf(stocks: List<Stock>): List<FinalPriceBatingStrategyV1> {
            return stocks.mapIndexed { rank, stock -> of(stock, rank + 1) }.filter { it.isValidCurrentValue() }
        }

        // TODO: 단건 validation의 경우에는 순위에 대한 보장이 필요하다
        fun validOf(stock: Stock): FinalPriceBatingStrategyV1? = validListOf(listOf(stock)).firstOrNull()
    }

    override val events: MutableList<DomainEvent>
        get() = mutableListOf()

    override fun complete() {
        events.add(
            StrategyCreatedEvent(
                stockId = this.stock.stockId.value,
                stockName = this.stock.stockName.value,
                savedAt = ZonedDateTime.now(), // TODO: event 발행 시점에 넣는 것이 아니라 객체가 이미 갖고 있도록.
                type = StrategyType.FinalPriceBatingV1,
            ),
        )
    }
}

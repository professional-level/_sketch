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
        
        fun of(stock: Stock, rank: Int): FinalPriceBatingStrategyV1 = FinalPriceBatingStrategyV1(stock, rank)
    }

    override val events: MutableList<DomainEvent> = mutableListOf()

    override fun complete() {
        // TODO: complete()를 AOP에서 수행하는데, 그렇게 하지말고 StrategyCreatedEvent의 경우에는 생성시점에 쌓이도록 하는게 좋겠다.
        events.add(
            StrategyCreatedEvent(
                stockId = this.stock.stockId.value,
                stockName = this.stock.stockName.value,
                savedAt = ZonedDateTime.now(), // TODO: event 발행 시점에 넣는 것이 아니라 객체가 이미 갖고 있도록.
                type = StrategyType.FinalPriceBatingV1,
            ),
        )
    }

    override fun project(domainEvent: DomainEvent) {
        TODO("Not yet implemented")
    }
}

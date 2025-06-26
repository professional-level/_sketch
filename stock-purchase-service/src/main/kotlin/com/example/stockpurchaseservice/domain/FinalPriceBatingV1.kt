package com.example.stockpurchaseservice.domain

import com.example.common.domain.event.DomainEvent
import com.example.common.domain.event.EventSupportedEntity
import common.StringExtension.isNotNull
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.math.round

// StockStrategy 인터페이스 구현
abstract class StockStrategy : EventSupportedEntity {
    abstract fun createPurchaseOrder(): PurchaseOrder?
    abstract fun createSellingOrder(orderId: OrderId): SellingOrder?
    abstract val purchasedAt: ZonedDateTime?
    fun isPurchased() = purchasedAt.isNotNull()
    abstract fun calculateQuantity(): Int
    // 실패시 매도에 대한 파라미터
    abstract val dueDateTime: ZonedDateTime?
    abstract fun isOverdue() : Boolean?
}

class FinalPriceBatingV1 private constructor(
    val stock: Stock,
    val requestedAt: ZonedDateTime,
    val purchasePrice: Money,
    override val purchasedAt: ZonedDateTime?,
) : StockStrategy() {
    /* TODO:
        1. 구매를 하는 행위에 대한 전략이 필요하다
        예를 들면, Target Price를 설정하는 전략
        목표 price가 1000원 이라면, 1000원에 70% 구매 900원에 15% 구매, 11000원에 15% 구매 등
        전략 타입에 따른, 목표금액의 분산화 로직 필요
        2. 매수 틱 단가를 계산하는 것이 필요하다.
        하나의 틱이 얼마인지 계산하여 몇 틱을 분산화 할 것 인지에 대한 로직이 필요
        3. 매수의 총 비용이 얼마인지를 반환하는 것이 필요하다.
        전략마다, 매수하는 금액도 다를 것이고, 총 매수 가능한 금액을 계산하는것도 필요하다.
     */

    // 이벤트 목록 관리
    override val events: MutableList<DomainEvent> = mutableListOf()

//    // 가격 격차 검증 로직
//    fun isValidPriceGap(): Boolean {
//        val priceDifference = currentPrice.price - purchasePrice.price
//        val percentageDifference = (priceDifference / purchasePrice.price) * 100
//        return percentageDifference <= ALLOWED_PRICE_GAP_PERCENTAGE
//    }

    // 구매 주문 생성
    override fun createPurchaseOrder(): PurchaseOrder? {
        // Return null
        if (isPurchased()) return null

        // 익절, 손절 가격 계산
        val takeProfitPrice = calculateTakeProfitPrice(purchasePrice)
        val stopLossPrice = calculateStopLossPrice(purchasePrice)

        val purchaseOrder = PurchaseOrder(
            stockId = stock.id,
            stockName = stock.name,
            purchasePrice = purchasePrice,
//            takeProfitPrice = takeProfitPrice, // TODO: PurchaseOrder에 strategy관련 parameter 제거 한것.
//            stopLossPrice = stopLossPrice,
            strategyType = StrategyType.FinalPriceBatingV1,
            requestedAt = requestedAt,
            purchasedAt = purchasedAt,
            quantity = calculateQuantity(),
            orderState = OrderState.PURCHASE_WAITING,
        )

        // 이벤트 추가
        events.add(
            StrategyExecutedEvent(
                stockId = stock.id.value,
                executedAt = ZonedDateTime.now(),
                type = StrategyType.FinalPriceBatingV1,
            ),
        )
        return purchaseOrder
    }

    override fun createSellingOrder(orderId: OrderId): SellingOrder? {
        if (!isPurchased()) return null
        // 익절, 손절 가격 계산
        val takeProfitPrice = calculateTakeProfitPrice(purchasePrice)
        val stopLossPrice = calculateStopLossPrice(purchasePrice)

        val sellingPrice = when(!isOverdue()){
            true -> takeProfitPrice
            false -> stopLossPrice
        } // TODO: 원래는 isOverdue()에 따라서 profit or loss만 계산하면 되지만, 가독성 및 흐름을 위해 둘 다 계산
        // TODO: 추후 사용하지 않는다면 성능 개선을 위해, 사용하는 값만 계산하도록 수정 필요

        return SellingOrder(
            id = orderId,
            stockId = stock.id,
            stockName = stock.name,
            requestedAt = requestedAt,
            strategyType = StrategyType.FinalPriceBatingV1,
            purchasedAt = purchasedAt!!, // TODO: 위에서 !isPurchased()를 했는데 타입체크가 왜 안될까?
            purchasePrice = purchasePrice,
            sellingPrice = sellingPrice, // TODO: 지금은 익절 가격을 매핑하지만, 날짜라던가 특정 조건에 따라서는 손절 가격을 매핑해야 한다.
            quantity = calculateQuantity(),
            orderState = TODO(),
        )
    }

    override fun calculateQuantity(): Int {
        return round(DEFAULT_BUY_TOTAL_AMOUNT / purchasePrice.price).toInt()
    }

    override val dueDateTime: ZonedDateTime?
        get() = purchasedAt?.plusDays(2)

    override fun isOverdue(): Boolean {
        return ZonedDateTime.now().isBefore(dueDateTime)
    }

    private fun calculateTakeProfitPrice(purchasePrice: Money): Money {
        return purchasePrice * (1 + TAKE_PROFIT_MARGIN)
    }

    private fun calculateStopLossPrice(purchasePrice: Money): Money {
        return purchasePrice * (1 - STOP_LOSS_MARGIN)
    }

    companion object {
        // 전략 설계
        private const val ALLOWED_PRICE_GAP_PERCENTAGE = 2.5 // 허용 가격 격차 퍼센트
        private const val TAKE_PROFIT_MARGIN = 0.03
        private const val STOP_LOSS_MARGIN = 0.03

        // 전략에 따른 구매 금액
        private const val DEFAULT_BUY_TOTAL_AMOUNT: Long = 1_000_000

        // 객체 생성용 팩토리 메서드
        fun of(
            stock: Stock,
            requestedAt: ZonedDateTime,
            purchasePrice: Money,
            purchasedAt: ZonedDateTime? = null,
        ): FinalPriceBatingV1 {
            return FinalPriceBatingV1(
                stock = stock,
                requestedAt = requestedAt,
                purchasePrice = purchasePrice,
                purchasedAt = purchasedAt,
            )
        }
    }

    // 이벤트 완료 처리
    override fun complete() {
        // 필요한 경우 추가 로직 수행
    }

    override fun project(domainEvent: DomainEvent) {
        TODO("Not yet implemented")
    }
}

// }
//
// class Order private constructor(
//    val id: OrderId,
//    val stockId: StockId,
//    val stockName: String,
//    val requestedAt: ZonedDateTime,
//    val strategyType: StrategyType,
//    val purchasedAt: ZonedDateTime?,
//    val sellingAt: ZonedDateTime?,
//    val purchasePrice: Money?,
//    val sellingPrice: Money?,
// ) {
//    companion object {
//        fun from(purchaseOrder: PurchaseOrder): Order {
//            return Order(
//                id = purchaseOrder.id,
//                stockId = purchaseOrder.stockId,
//                stockName = purchaseOrder.stockName,
//                requestedAt = purchaseOrder.requestedAt,
//                purchasePrice = purchaseOrder.purchasePrice,
//                strategyType = purchaseOrder.strategyType,
//                purchasedAt = purchaseOrder.events.find { it is PurchaseSuccessEvent }?.occurredAt,
//                sellingAt = null,
//                sellingPrice = null,
//            )
//        }
//
//        fun from(sellingOrder: SellingOrder): Order {
//            TODO()
//        }
//    }
// }
// TODO: 판매 금액의 spread 분산 전략 적용 필요. +- 1% 등등
sealed class Order(
    open val id: OrderId,
    open val stockId: StockId,
    open val stockName: String,
    open val requestedAt: ZonedDateTime,
    open val strategyType: StrategyType,
    open val purchasePrice: Money,
    open val purchasedAt: ZonedDateTime?,
    open val quantity: Int,
    open val orderState: OrderState,
) : EventSupportedEntity {
    abstract override val events: MutableList<DomainEvent>
    abstract override fun complete()
    abstract fun changeOrderState(orderState: OrderState)
}

// 판매 주문 엔티티
// class SellingOrder : Order() {
//    override val events: MutableList<DomainEvent> = mutableListOf()
//
//    override fun complete() {
//        TODO("Not yet implemented")
//    }
//    companion object{
//        fun create(): SellingOrder{
//            return SellingOrder(
//
//            )
//        }
//    }
// }
// TODO: data class로 바꿔도 되는지 확인
data class SellingOrder(
    override val id: OrderId,
    override val stockId: StockId,
    override val stockName: String,
    override val requestedAt: ZonedDateTime,
    override val strategyType: StrategyType,
    override val purchasedAt: ZonedDateTime,
    override val purchasePrice: Money,
    override val quantity: Int,
    override val orderState: OrderState,
    val sellingPrice: Money,
) : Order(id, stockId, stockName, requestedAt, strategyType, purchasePrice, purchasedAt, quantity, orderState) {
    override val events: MutableList<DomainEvent> = mutableListOf()

    override fun complete() {
        // 필요한 완료 로직 구현
    }

    override fun changeOrderState(orderState: OrderState) {
        // TODO: 추후 state 변경에 따라 event 발행 필요
        when (orderState) {
            OrderState.PURCHASE_WAITING -> {}
            OrderState.PURCHASE_IN_PROCESS -> {}
            OrderState.PURCHASE_COMPLETED -> {}
            OrderState.SELLING_WAITING -> {}
            OrderState.SELLING_IN_PROCESS -> {}
            OrderState.SELLING_COMPLETED -> {}
        }
        if (this.orderState != orderState)
            register(
                ChangeOrderStateEvent(
                    orderId = this.id,
                    orderState = orderState,
                ),
            )
    }

    override fun project(domainEvent: DomainEvent) {
       when(domainEvent){
              is SellingSuccessEvent -> {}
              is SellingFailedEvent -> {}
              is ChangeOrderStateEvent -> this.copy(orderState = domainEvent.orderState)
       }
    }

    companion object {
        fun create(
            id: OrderId,
            stockId: StockId,
            stockName: String,
            requestedAt: ZonedDateTime,
            strategyType: StrategyType,
            sellingPrice: Money,
            purchasePrice: Money,
            purchasedAt: ZonedDateTime,
            quantity: Int,
            orderState: OrderState,
        ): SellingOrder {
            return SellingOrder(
                id = id,
                stockId = stockId,
                stockName = stockName,
                requestedAt = requestedAt,
                strategyType = strategyType,
                sellingPrice = sellingPrice,
                purchasePrice = purchasePrice,
                purchasedAt = purchasedAt,
                quantity = quantity,
                orderState = orderState,
            )
        }

        fun from(
            id: OrderId,
            stockId: StockId,
            stockName: String,
            requestedAt: ZonedDateTime,
            strategyType: StrategyType,
            sellingPrice: Money,
            purchasePrice: Money,
            purchasedAt: ZonedDateTime,
            quantity: Int,
            orderState: OrderState,
        ): SellingOrder {
            return SellingOrder(
                id = id,
                stockId = stockId,
                stockName = stockName,
                requestedAt = requestedAt,
                strategyType = strategyType,
                sellingPrice = sellingPrice,
                purchasePrice = purchasePrice,
                purchasedAt = purchasedAt,
                quantity = quantity,
                orderState = orderState,
            )
        }
    }
}

enum class OrderState {
    PURCHASE_WAITING,  // 주문이 들어가기 전
    PURCHASE_IN_PROCESS, // 주문이 들어간 상태
    PURCHASE_COMPLETED, // 체결이 된 상태
    SELLING_WAITING,  // 주문이 들어가기 전
    SELLING_IN_PROCESS, // 주문이 들어간 상태
    SELLING_COMPLETED, // 체결이 된 상태
}

data class SellingSuccessEvent(
    val orderId: OrderId,
) : DomainEvent()

data class SellingFailedEvent(
    val orderId: OrderId,
    val message: String,
    val errorCode: SellingErrorCode,
) : DomainEvent()

data class ChangeOrderStateEvent(
    val orderId: OrderId,
    val orderState: OrderState,
) : DomainEvent()

enum class SellingErrorCode {
    UNDEFINED,
}

//
// // 구매 주문 엔티티
// data class PurchaseOrder(
//    val stockId: StockId,
//    val stockName: String, // TODO: String 말고 Vo로?
//    val purchasePrice: Money,
//    val takeProfitPrice: Money,
//    val stopLossPrice: Money,
//    val strategyType: StrategyType,
//    val requestedAt: ZonedDateTime,
//    val id: OrderId = OrderId.generate(),
// ) : EventSupportedEntity {
//    override val events: MutableList<DomainEvent> = mutableListOf()
//
//    private var _isSuccess: Boolean = false
//    val isSuccess get() = _isSuccess
//    fun success() {
//        _isSuccess = true
//        events.add(PurchaseSuccessEvent(orderId = this.id))
//    }
//
//    fun failed(message: String?, purchaseErrorCode: PurchaseErrorCode = PurchaseErrorCode.UNDEFINED) {
//        _isSuccess = false
//        events.add(PurchaseFailedEvent(message = message ?: "", errorCode = purchaseErrorCode))
//    }
//
//    override fun complete() {
//        TODO("Not yet implemented")
//    }
// }
data class PurchaseOrder(
    override val id: OrderId = OrderId.generate(),
    override val stockId: StockId,
    override val stockName: String,
    override val requestedAt: ZonedDateTime,
    override val strategyType: StrategyType,
    override val purchasePrice: Money,
    override val purchasedAt: ZonedDateTime?,
    override val quantity: Int,
    override val orderState: OrderState,
//    val takeProfitPrice: Money,
//    val stopLossPrice: Money,
) : Order(id, stockId, stockName, requestedAt, strategyType, purchasePrice, purchasedAt, quantity, orderState) {
    override val events: MutableList<DomainEvent> = mutableListOf()
    private var _isSuccess: Boolean = false
    val isSuccess get() = _isSuccess

    fun success() {
        _isSuccess = true
        register(PurchaseSuccessEvent(orderId = this.id))
    }

    fun failed(message: String?, purchaseErrorCode: PurchaseErrorCode = PurchaseErrorCode.UNDEFINED) {
        _isSuccess = false
        register(PurchaseFailedEvent(message = message ?: "", errorCode = purchaseErrorCode))
    }

    override fun complete() {
        // 필요한 완료 로직 구현
    }

    override fun changeOrderState(orderState: OrderState) {
        // TODO: 추후 state 변경에 따라 event 발행 필요
        when (orderState) {
            OrderState.PURCHASE_WAITING -> {}
            OrderState.PURCHASE_IN_PROCESS -> {}
            OrderState.PURCHASE_COMPLETED -> {}
            OrderState.SELLING_WAITING -> {}
            OrderState.SELLING_IN_PROCESS -> {}
            OrderState.SELLING_COMPLETED -> {}
        }
        if (this.orderState != orderState)
            register(
                ChangeOrderStateEvent(
                    orderId = this.id,
                    orderState = orderState,
                ),
            )
    }

    override fun project(domainEvent: DomainEvent) {
        when (domainEvent) {
            is PurchaseSuccessEvent -> this.copy(orderState = OrderState.PURCHASE_IN_PROCESS) // TODO: val이기 때문에 copy만으로는 반환값을 이용하지 않는 한 변경이 없다.
            is PurchaseFailedEvent -> this.copy(orderState = OrderState.PURCHASE_WAITING)
            is ChangeOrderStateEvent -> this.copy(orderState = domainEvent.orderState)
        }
    }
}

// TODO: stockId, type을 넘기는 것 보다는,,, traceId, spanId의 개념을 구현하는게 더 좋을 것 같다.
// 동기코드에선 ThreadLocal을 구현하면 되는데.. 비동기 webflux& 코루틴에서는 어떻게 구현해야 할지...
data class PurchaseSuccessEvent(
    val orderId: OrderId,
) : DomainEvent()

data class PurchaseFailedEvent(
    val message: String,
    val errorCode: PurchaseErrorCode,
) : DomainEvent()

enum class PurchaseErrorCode { // TODO: error enum class의 공통분모를 상속하도록 수정 필요
    UNDEFINED,
}

data class StrategyExecutedEvent(
    val stockId: String,
    val executedAt: ZonedDateTime,
    val type: StrategyType,
) : DomainEvent()

enum class StrategyType {
    Undefined,
    FinalPriceBatingV1,
}

// 기타 필요한 클래스와 인터페이스
data class Stock(
    val id: StockId,
    val name: String,
    // 기타 필요한 정보
)

@JvmInline
value class StockId(val value: String)

@JvmInline
value class OrderId(val value: UUID) {
    companion object {
        fun generate(): OrderId = OrderId(UUID.randomUUID())
    }
}

@JvmInline
value class Money(val price: Double) {
    operator fun times(factor: Double): Money = Money(price * factor)
    operator fun minus(other: Money): Money = Money(price - other.price)
    operator fun plus(other: Money): Money = Money(price + other.price)

    init {
        require(price >= 0.0)
    }

    companion object {
        fun undefined(): Money = Money(0.0)
    }
}

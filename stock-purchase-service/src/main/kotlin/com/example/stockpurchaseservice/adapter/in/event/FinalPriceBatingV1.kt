import com.example.common.domain.event.DomainEvent
import com.example.common.domain.event.EventSupportedEntity
import java.time.ZonedDateTime
import java.util.UUID

// StockStrategy 인터페이스 구현
interface StockStrategy : EventSupportedEntity

class FinalPriceBatingV1 private constructor(
    val stock: Stock,
    val requestedAt: ZonedDateTime,
    val strategyType: StrategyType,
    val purchasePrice: Money,
    val currentPrice: Money,
) : StockStrategy {

    // 이벤트 목록 관리
    override val events: MutableList<DomainEvent> = mutableListOf()

    init {
//        // 객체 생성 시점에 검증 로직 수행
//        if (!isValidPriceGap()) {
//            throw InvalidPurchaseException("가격 격차가 허용 범위를 초과했습니다.")
//        }
    }

    // 가격 격차 검증 로직
    private fun isValidPriceGap(): Boolean {
        val priceDifference = currentPrice.price - purchasePrice.price
        val percentageDifference = (priceDifference / purchasePrice.price) * 100
        return percentageDifference <= ALLOWED_PRICE_GAP_PERCENTAGE
    }

    // 구매 주문 생성
    fun createPurchaseOrder(): PurchaseOrder {
        // 익절, 손절 가격 계산
        val takeProfitPrice = calculateTakeProfitPrice(purchasePrice)
        val stopLossPrice = calculateStopLossPrice(purchasePrice)

        val purchaseOrder = PurchaseOrder(
            stockId = stock.stockId,
            purchasePrice = purchasePrice,
            takeProfitPrice = takeProfitPrice,
            stopLossPrice = stopLossPrice,
            strategyType = strategyType,
            requestedAt = requestedAt
        )

        // 이벤트 추가
        events.add(
            StrategyExecutedEvent(
                stockId = stock.stockId.value,
                executedAt = ZonedDateTime.now(),
                type = strategyType,
            )
        )
        return purchaseOrder
    }

    private fun calculateTakeProfitPrice(purchasePrice: Money): Money {
        return purchasePrice * (1 + TAKE_PROFIT_MARGIN)
    }

    private fun calculateStopLossPrice(purchasePrice: Money): Money {
        return purchasePrice * (1 - STOP_LOSS_MARGIN)
    }

    companion object {
        private const val ALLOWED_PRICE_GAP_PERCENTAGE = 2.0 // 허용 가격 격차 퍼센트
        private const val TAKE_PROFIT_MARGIN = 0.05
        private const val STOP_LOSS_MARGIN = 0.05

        // 객체 생성용 팩토리 메서드
        fun of(
            stock: Stock,
            requestedAt: ZonedDateTime,
            strategyType: StrategyType,
            purchasePrice: Money,
            currentPrice: Money,
        ): FinalPriceBatingV1 {
            return FinalPriceBatingV1(
                stock = stock,
                requestedAt = requestedAt,
                strategyType = strategyType,
                purchasePrice = purchasePrice,
                currentPrice = currentPrice
            )
        }
    }

    // 이벤트 완료 처리
    override fun complete() {
        // 필요한 경우 추가 로직 수행
    }
}

// 예외 클래스
class InvalidPurchaseException(message: String) : RuntimeException(message)

// 구매 주문 엔티티
data class PurchaseOrder(
    val stockId: StockId,
    val purchasePrice: Money,
    val takeProfitPrice: Money,
    val stopLossPrice: Money,
    val strategyType: StrategyType,
    val requestedAt: ZonedDateTime,
    val id: OrderId = OrderId.generate(),
)

data class StrategyExecutedEvent(
    val stockId: String,
    val executedAt: ZonedDateTime,
    val type: StrategyType,
) : DomainEvent {
    override val id: UUID = UUID.randomUUID()
    override val occurredAt: ZonedDateTime = ZonedDateTime.now()
}

enum class StrategyType {
    Undefined,
    FinalPriceBatingV1,
}

// 기타 필요한 클래스와 인터페이스
data class Stock(
    val stockId: StockId,
    val name: String,
    // 기타 필요한 정보
)

@JvmInline
value class StockId(val value: String)

@JvmInline
value class OrderId(val value: String) {
    companion object {
        fun generate(): OrderId = OrderId(java.util.UUID.randomUUID().toString())
    }
}

@JvmInline
value class Money(val price: Double) {
    operator fun times(factor: Double): Money = Money(price * factor)
    operator fun minus(other: Money): Money = Money(price - other.price)
    operator fun plus(other: Money): Money = Money(price + other.price)
}
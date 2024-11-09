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
    val purchasedAt: ZonedDateTime?,
) : StockStrategy {

    val isPurchased: Boolean = (purchasedAt == null)

    // 이벤트 목록 관리
    override val events: MutableList<DomainEvent> = mutableListOf()

//    // 가격 격차 검증 로직
//    fun isValidPriceGap(): Boolean {
//        val priceDifference = currentPrice.price - purchasePrice.price
//        val percentageDifference = (priceDifference / purchasePrice.price) * 100
//        return percentageDifference <= ALLOWED_PRICE_GAP_PERCENTAGE
//    }

    // 구매 주문 생성
    fun createPurchaseOrder(): PurchaseOrder {
        // 익절, 손절 가격 계산
        val takeProfitPrice = calculateTakeProfitPrice(purchasePrice)
        val stopLossPrice = calculateStopLossPrice(purchasePrice)

        val purchaseOrder = PurchaseOrder(
            stockId = stock.id,
            purchasePrice = purchasePrice,
            takeProfitPrice = takeProfitPrice,
            stopLossPrice = stopLossPrice,
            strategyType = strategyType,
            requestedAt = requestedAt,
        )

        // 이벤트 추가
        events.add(
            StrategyExecutedEvent(
                stockId = stock.id.value,
                executedAt = ZonedDateTime.now(),
                type = strategyType,
            ),
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
        // 전략 설계
        private const val ALLOWED_PRICE_GAP_PERCENTAGE = 2.5 // 허용 가격 격차 퍼센트
        private const val TAKE_PROFIT_MARGIN = 0.03
        private const val STOP_LOSS_MARGIN = 0.03

        // 객체 생성용 팩토리 메서드
        fun of(
            stock: Stock,
            requestedAt: ZonedDateTime,
            strategyType: StrategyType,
            purchasePrice: Money,
            purchasedAt: ZonedDateTime? = null,
        ): FinalPriceBatingV1 {
            return FinalPriceBatingV1(
                stock = stock,
                requestedAt = requestedAt,
                strategyType = strategyType,
                purchasePrice = purchasePrice,
                purchasedAt = purchasedAt,
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
    val id: StockId,
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

package com.example.stockpurchaseservice.application.scheduler

import com.example.stockpurchaseservice.domain.ExecutedStock
import com.example.stockpurchaseservice.domain.ExecutionType
import com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import com.example.common.application.event.ApplicationEvent
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.service.strategy.toDto
import com.example.stockpurchaseservice.domain.FinalPriceBatingV1
import com.example.stockpurchaseservice.domain.Order
import com.example.stockpurchaseservice.domain.SellingOrder
import com.example.stockpurchaseservice.domain.Stock
import com.example.stockpurchaseservice.domain.StrategyType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.retry.annotation.Recover
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID

@Component
internal class StockTradeScheduler(
    private val stockOrderRepository: StockOrderRepository,
//    private val stockInformationRepository: StockInformationRepository, // TODO: common? 아니면 독립적으로 ?
    private val marketService: MarketServicePort,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    // 체결에 대한 변경을 감지. 하루마다 초기화. 추후 redis로 변경 필요
    // 서비스의 이상 정지 시, 데이터의 비정합성을 복구할 방법을 찾아야 함 리실리언스 적용 필요
    private val executionQueue: MutableSet<ExecutedStock> = java.util.concurrent.ConcurrentHashMap.newKeySet()
//        ConcurrentLinkedQueue()

    @Scheduled(cron = "0 */1 * ? * MON-FRI") // TODO: 판매 전략에 따라 스케쥴 시간 변경필요
    suspend fun sellOrderByStrategies() {
        // TODO: 휴장일인지 체크하는 로직이 필요
        val dateTime = ZonedDateTime.now()

        // 팔리지 않은 모든 종목 가져오기
        val orders = stockOrderRepository.findAllNotCompleted()
        val sellingOrderList = orders.mapNotNull { order ->
            when (order.strategyType) {
                StrategyType.Undefined -> throw RuntimeException("Undefined strategy type ${order.strategyType}") // TODO: exception mapping
                StrategyType.FinalPriceBatingV1 -> {
                    val strategy = FinalPriceBatingV1.of(
                        stock = Stock(id = order.stockId, name = order.stockName),
                        requestedAt = order.requestedAt,
                        purchasePrice = order.purchasePrice,
                        purchasedAt = order.purchasedAt,
                    )
                    val sellingOrder = strategy.createSellingOrder(order.id)
                    if (sellingOrder == null) println("selling order unexpected error") // TODO: 로깅 필요
                    sellingOrder
                }
            }
        }

        println(sellingOrderList)

        /*db save 로직*/
        /* 이미 팔렸을 경우 + @ transaction rollback 처리 필요*/
        sellingOrderList.forEach { order ->
            stockOrderRepository.save(order)
            marketService.sellStock(order.toDto())
        }
    }

    @Recover
    suspend fun recover(e: Exception) {
        // 최대 재시도 횟수를 초과했을 때 수행할 동작
        println("재시도 횟수를 초과했습니다: ${e.message}")
        // 예: 알림 전송, 로그 기록, 대체 로직 실행 등
        TODO()
    }

    @Scheduled(cron = "0 */1 * ? * MON-FRI") // TODO: 판매 전략에 따라 스케쥴 시간 변경필요
    suspend fun executionCheck() {
        val executedStockList: List<ExecutedStock> = marketService.findExecutionListAtOneDay()
        // dirty check를 통해, 변경이 없으면 바로 return 처리
        val refinedExecutedStockList = executedStockList.filter { execution ->
            executionQueue.add(execution) // dirty check를 통해, 큐의 변경이 된 경우에만 list로 정제한다.
            // Tip. set의 add() 메서드의 반환값은 boolean이다
        }
        if (refinedExecutedStockList.isEmpty()) return

        val (selled, purchased) = refinedExecutedStockList.partition { it.type == ExecutionType.Selling }
        // TODO: 추후 event publish 형태로 변경 필요;;
        // TODO: 지금 전제가 같은 종목이 다른 전략에 의해 발굴 되지 않는다는 전제가 있으므로, 추후 해결 필요
        // TODO: 수량을 제거(정확히는 전략에 맞게 수량이상이 충족되면 상태를 변경 할 수 있도록)하고 전략의 우선순위에 맞게 수행 되도록 변경 필요
        // TODO: 일부만 수행 되었을 경우, 다음 스케쥴러에서 다시 수행 되도록 변경 필요(State 변경 처리)
        // TODO: 우선 문제를 최소화 하기 위해, 매도 가격은 1개로 정의. 그렇다면 해당 매도 가격에 따른 수량은 고정 될 것이니 그것으로 처리 필요.
        selled.forEach { item ->
            // TODO: findByStockIdAndQuantity가 아닌 findByOrder()를 통해 매수,매도 가격까지 넘겨줘야 할 듯
//            val tempOrder = stockOrderRepository.findByOrder(order)
//            val order = stockOrderRepository.findByStockIdAndQuantity(item.stock.id, item.quantity)?.apply {
//                // TODO: OrderState를 Completed로 변경하는 로직은 여기가 아닌, 모든 execution이 처리된 이벤트를 조합해서 변경(KafkaStream)
// //                changeOrderState(OrderState.SELLING_COMPLETED) // TODO: order은 sealed class인데, cast 하지 않고 이렇게 처리 가능한지 확인 필요
//            }
//            order?.let { stockOrderRepository.save(it) }
//            // TODO: 정상적인 상황에서는 order가 존재 해야 한다. 없을 경우 예외 혹은 이벤트 or 로그 처리 필요
        }
        // TODO: 여기서는 selled 보다 purchased에 대한 정보의 처리가 중요하다
        purchased.forEach { item ->
            // TODO:findByExternalOrderId를 사용해도 좋지만 purchase에서 OrderId의 정보를 가지고 있게 하는것이 좋을 거 같기도 하다.
            val order = stockOrderRepository.findByExternalOrderId(item.externalOrderId)?.let { order ->
                makeSellOrderByStrategy(order)
            }
            order?.let {
                // TODO: sell 할때 수량을 item에서 받아와야 . domain logic의 quantity는 totalQuantity로 변경을 하던 변경에 상관 없도록 수정 필요.
                marketService.sellStock(it.toDto(quantity = item.quantity)) // 구매한 수량만큼 바로 매도
                // TODO: event 처리는 어디서?
                // TODO: 매수 된 양을 이벤트로 던져 KafkaStream에서 합산해야 함
            }
        }
    }

    private suspend fun makeSellOrderByStrategy(order: Order): SellingOrder? {
        return when (order.strategyType) {
            StrategyType.Undefined -> throw RuntimeException(
                "Undefined strategy type ${order.strategyType}",
            ) // TODO: exception mapping
            StrategyType.FinalPriceBatingV1 -> {
                val strategy = FinalPriceBatingV1.of(
                    stock = Stock(id = order.stockId, name = order.stockName),
                    requestedAt = order.requestedAt,
                    purchasePrice = order.purchasePrice,
                    purchasedAt = order.purchasedAt,
                )
                val sellingOrder = strategy.createSellingOrder(order.id)
                if (sellingOrder == null) println("selling order unexpected error") // TODO: 로깅 필요
                sellingOrder
            }
        }
    }

    @Scheduled(cron = "0 10 8 ? * MON-FRI") // 매일 주문이 초기화 되는 것을 구현
    fun initializeExecutionQueue() {
        // TODO: 초기화 하기 전에 데이터 정리 및 이벤트 발행 필요
        // executionQueue를 빈 값으로 초기화
        executionQueue.clear()
    }
}

class SellingExecutionCreatedApplicationEvent(
    val stockId: String,
    val quantity: Int,
    override val id: UUID,
    override val occurredAt: ZonedDateTime,
) : ApplicationEvent

class PurchaseExecutionCreatedApplicationEvent(
    val stockId: String,
    val quantity: Int,
    override val id: UUID,
    override val occurredAt: ZonedDateTime,
) : ApplicationEvent

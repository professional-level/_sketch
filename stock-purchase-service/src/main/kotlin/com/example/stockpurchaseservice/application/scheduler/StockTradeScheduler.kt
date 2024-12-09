package com.example.com.example.stockpurchaseservice.application.scheduler

import com.example.com.example.stockpurchaseservice.domain.ExecutedStock
import com.example.com.example.stockpurchaseservice.domain.ExecutionType
import com.example.com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import com.example.common.application.event.ApplicationEvent
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.service.strategy.toDto
import com.example.stockpurchaseservice.domain.FinalPriceBatingV1
import com.example.stockpurchaseservice.domain.Stock
import com.example.stockpurchaseservice.domain.StrategyType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.retry.annotation.Recover
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

@Component
internal class StockTradeScheduler(
    private val stockOrderRepository: StockOrderRepository,
//    private val stockInformationRepository: StockInformationRepository, // TODO: common? 아니면 독립적으로 ?
    private val marketService: MarketServicePort,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    private val executionQueue: ConcurrentLinkedQueue<ExecutedStock> = ConcurrentLinkedQueue()
    // example of cron = "초 분 시간-시간 ? * 요일-요일"
    //    @Scheduled(cron = "15 */2 9-18 ? * MON-FRI ")
    @Scheduled(cron = "15 */1 * ? * MON-FRI ")
    fun sample() {
        val time = ZonedDateTime.now()
        println("[${Thread.currentThread()}]:$time")
    }

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
//        val orders = stockOrderRepository.findAllNotCompleted()
//        val purchaseOrder = orders.map { it as PurchaseOrder }
//        val sellingOrder = orders.map { it as SellingOrder }
        val executedStockList: List<ExecutedStock> = marketService.findExecutionListAtOneDay()
        val (selled, purchased) = executedStockList.partition { it.type == ExecutionType.Selling }
        // TODO: 추후 event publish 형태로 변경 필요;;
        // TODO: 지금 전제가 같은 종목이 다른 전략에 의해 발굴 되지 않는다는 전제가 있으므로, 추후 해결 필요
        selled.forEach { item ->
            val order = stockOrderRepository.findByStockIdAndQuantity(item.stock.id, item.quantity)?.let{
                it.orderState
            }
            order?.let{stockOrderRepository.save(it)}
        }
        purchased.forEach { item ->

        }
    }

    @Scheduled(cron = "0 30 8 ? * MON-FRI") // TODO: 판매 전략에 따라 스케쥴 시간 변경필요
    fun initializeExecutionQueue(){
        // executionQueue를 빈 값으로 초기화
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
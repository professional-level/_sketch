package com.example.com.example.stockpurchaseservice.application.scheduler

import com.example.com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.service.strategy.toDto
import com.example.stockpurchaseservice.domain.FinalPriceBatingV1
import com.example.stockpurchaseservice.domain.Stock
import com.example.stockpurchaseservice.domain.StrategyType
import org.springframework.retry.annotation.Recover
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
internal class StockTradeScheduler(
    private val stockOrderRepository: StockOrderRepository,
//    private val stockInformationRepository: StockInformationRepository, // TODO: common? 아니면 독립적으로 ?
    private val marketService: MarketServicePort,
) {
    // example of cron = "초 분 시간-시간 ? * 요일-요일"
    //    @Scheduled(cron = "15 */2 9-18 ? * MON-FRI ")
    @Scheduled(cron = "15 */1 * ? * MON-FRI ")
    fun sample() {
        val time = ZonedDateTime.now()
        println("[${Thread.currentThread()}]:$time")
    }

    @Scheduled(cron = "0 */1 * ? * MON-FRI") // TODO: 판매 전략에 따라 스케쥴 시간 변경필요
    suspend fun finalPriceBatingStrategy1() {
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
}

// private fun makeMockProgramVolumeAdaptedList(): List<FinalPriceBatingStrategyV1> {
//    return listOf(
//        FinalPriceBatingStrategyV1(
//            stock = Stock.of(
//                stockId = StockId.of("elementum"),
//                stockName = StockName.of("dapibus"),
//                stockPrice = StockPrice.of(3945),
//                stockDerivative = StockDerivative.of(value = 2.3),
//                stockVolume = StockVolume.of(value = 3917),
//                stockTotalVolume = StockTotalVolume.of(value = 6371),
//            ),
//            rank = 1,
//            foreignerStockVolume = FinalPriceBatingStrategyV1.ForeignerStockVolume(value = 4058),
//        ),
//    )
// }

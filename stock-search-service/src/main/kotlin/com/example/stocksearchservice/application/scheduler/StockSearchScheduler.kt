package com.example.stocksearchservice.application.scheduler

import com.example.stocksearchservice.domain.StockLog
import com.example.stocksearchservice.domain.repository.StockInformationRepository
import com.example.stocksearchservice.domain.strategy.FinalPriceBatingStrategyV1
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class StockSearchScheduler(
    private val stockInformationRepository: StockInformationRepository,
) {
    // example of cron = "초 분 시간-시간 ? * 요일-요일"
    //    @Scheduled(cron = "15 */2 9-18 ? * MON-FRI ")
    @Scheduled(cron = "15 */1 * ? * MON-FRI ")
    fun sample() {
        val time = ZonedDateTime.now()
        println("[${Thread.currentThread()}]:$time")
    }

    @Scheduled(cron = "15 */1 * ? * MON-FRI ")
//    @Scheduled(cron = "15 */2 9-18 ? * MON-FRI ")
    suspend fun findTop10VolumeStocks() {
        val stockList = stockInformationRepository.findTop10VolumeStocks()
        println(stockList)
        val stockLogList = StockLog.from(stockList) // TODO: 비동기에 맞춰서 로직 변경 해야 함
        stockInformationRepository.saveTop10VolumeStocks(stockLogList) // TODO: save 하는 로직을 event로 떨궈도 될듯?
    }

    // TODO: 해당 알고리즘을 관리하는것을 어떻게 해야 할지 고민이 필요.
    @Scheduled(cron = "0 50 15 ? * MON-FRI")
    suspend fun finalPriceBatingStrategy1() {
        // TODO: 휴장일인지 체크하는 로직이 필요
        val top10VolumeStockList = stockInformationRepository.findTop10VolumeStocks() // TODO: 조회 실패에 대한 retry 관련 로직 필요
        /*
         * 당일 거래대금 상위 10
         * 당일 거래대금 30이상
         * 당일 상승률 0% 이상 // TODO: 조건 확인
         * */
        val validStocks = FinalPriceBatingStrategyV1.validListOf(top10VolumeStockList)

        /*
         * 프로그램 순매수가 시가총액의 50% ?
         * 프로그램 순매수의 5거래일중 최대
         *
         * */
        // TODO: api 호출 횟수에 대한 지연을 생각 해야함

        val programVolumeList = validStocks.map {
            stockInformationRepository.getProgramPureBuyingVolumeAtEndOfDay(it.stock.stockId)
        }
        /*
         * 거래량이 5거래일중 최대치
         * */
    }
}

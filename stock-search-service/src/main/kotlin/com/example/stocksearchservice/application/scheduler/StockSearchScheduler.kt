package com.example.stocksearchservice.application.scheduler

import com.example.stocksearchservice.domain.Stock
import com.example.stocksearchservice.domain.StockDerivative
import com.example.stocksearchservice.domain.StockId
import com.example.stocksearchservice.domain.StockLog
import com.example.stocksearchservice.domain.StockName
import com.example.stocksearchservice.domain.StockPrice
import com.example.stocksearchservice.domain.StockTotalVolume
import com.example.stocksearchservice.domain.StockVolume
import com.example.stocksearchservice.domain.repository.FinalPriceBatingStrategyV1Repository
import com.example.stocksearchservice.domain.repository.StockInformationRepository
import com.example.stocksearchservice.domain.service.FinalPriceBatingStockAnalyzer
import com.example.stocksearchservice.domain.strategy.FinalPriceBatingStrategyV1
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
internal class StockSearchScheduler(
    private val stockInformationRepository: StockInformationRepository,
    private val finalPriceBatingStrategyV1Repository: FinalPriceBatingStrategyV1Repository,
    private val stockAnalyzer: FinalPriceBatingStockAnalyzer,
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
//    @Scheduled(cron = "0 50 15 ? * MON-FRI")
    // TODO: retry 동작 안함
    @Retryable( // TODO: 실패를 고려해 임의로 retryable 처리.
        value = [Exception::class],
        maxAttempts = 5,
        backoff = Backoff(delay = 1000)
    )
    @Scheduled(cron = "0 */1 * ? * MON-FRI")
    suspend fun finalPriceBatingStrategy1() {
        // TODO: 휴장일인지 체크하는 로직이 필요
        // TODO: dateTime을 여기서 받을것인지, 혹은 findTop10VolumeStocks시점에서 가져와야할지 고민 필요
        val dateTime = ZonedDateTime.now()
        val top10VolumeStockList = stockInformationRepository.findTop10VolumeStocks() // TODO: 조회 실패에 대한 retry 관련 로직 필요
        /*
         * 당일 거래대금 상위 10
         * 당일 거래대금 20이상
         * 당일 상승률 0% 이상 // TODO: 조건 확인
         * */

        /* stock list를 FinalPrice~ 객체로 초기화 할때, validation을 체크하는 로직이 필요 할 것 같다. fun create 반환 타입 nullable*/
        val validStocks = stockAnalyzer.analyzeStocks(top10VolumeStockList)
        /*
         * 프로그램 순매수가 시가총액의 3% ?
         * 프로그램 순매수의 5거래일중 최대
         *
         * */

        // TODO: api 호출 횟수에 대한 지연을 생각 해야함
        // TODO: cache 적용 고려
        val programVolumeAdaptedList = validStocks.map { entity ->
            val foreignerVolume =
                stockInformationRepository.getProgramPureBuyingVolumeAtLatestOfDay(entity.stock.stockId)
            entity.setForeignerStockVolume(foreignerVolume?.value ?: -1) // TODO: null일경우 -1로 구성하는게 가능할지 필요
            entity
        } /*프로그램의 순매수량이 시가총액의 3% 이상*/
            .filter { it.isValidProgramForeignerTradeVolume() }
            /*프로그램 순매수 5거래일중 최대*/
            .filter { stockInformationRepository.isHighestProgramVolumeIn5Days(id = it.stock.stockId) }
        // TODO: 2개의 repository를 동시에 호출 하는 성능 중심 vs filter를 걸어 호출 하는 객체 list를 작게 하는 전략 고민

        println(programVolumeAdaptedList)
        /*
         * 거래량이 3거래일중 최대치
         * 거래량이 5거래일중 최고치중 70% 이상(당일 제외)
         * */

        /*db save 로직*/
        finalPriceBatingStrategyV1Repository.saveAll(programVolumeAdaptedList)
//        finalPriceBatingStrategyV1Repository.saveAll(makeMockProgramVolumeAdaptedList())
        // debuging code
//        programVolumeAdaptedList.forEach finalPriceBatingStrategyV1Repository.save(it) }
//        FinalPriceBatingStrategyV1.default().let { finalPriceBatingStrategyV1Repository.save(it) }
//        FinalPriceBatingStrategyV1.default().let(::listOf).let { finalPriceBatingStrategyV1Repository.saveAll(it) }

        /*매수를 위한 microservice로 데이터 이관 로직*/
        /* event publish를 통해 해결*/
//        throw RuntimeException("일부로 throw")
    }
}

// TODO: 추후 테스트 mock으로 이동
private fun makeMockProgramVolumeAdaptedList(): List<FinalPriceBatingStrategyV1> {
    return listOf(
        FinalPriceBatingStrategyV1(
            stock = Stock.of(
                stockId = StockId.of("elementum"),
                stockName = StockName.of("dapibus"),
                stockPrice = StockPrice.of(3945),
                stockDerivative = StockDerivative.of(value = 2.3),
                stockVolume = StockVolume.of(value = 3917),
                stockTotalVolume = StockTotalVolume.of(value = 6371),
            ),
            rank = 1,
            foreignerStockVolume = FinalPriceBatingStrategyV1.ForeignerStockVolume(value = 4058),
        ),
    )
}

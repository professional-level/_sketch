package com.example.com.example.stockpurchaseservice.application.scheduler

import org.springframework.retry.annotation.Recover
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
internal class StockTradeScheduler(
    // TODO: repository 추가
) {
    // example of cron = "초 분 시간-시간 ? * 요일-요일"
    //    @Scheduled(cron = "15 */2 9-18 ? * MON-FRI ")
    @Scheduled(cron = "15 */1 * ? * MON-FRI ")
    fun sample() {
        val time = ZonedDateTime.now()
        println("[${Thread.currentThread()}]:$time")
    }

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
        val validStocks = FinalPriceBatingStrategyV1.validListOf(
            top10VolumeStockList,
        ) // TODO: 이 단계에서 FinalPriceBatingStrategyV1에 rank를 넣는 것 빼야할지도
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
//        finalPriceBatingStrategyV1Repository.saveAll(programVolumeAdaptedList)
        finalPriceBatingStrategyV1Repository.saveAll(makeMockProgramVolumeAdaptedList())
        // debuging code
//        programVolumeAdaptedList.forEach finalPriceBatingStrategyV1Repository.save(it) }
//        FinalPriceBatingStrategyV1.default().let { finalPriceBatingStrategyV1Repository.save(it) }
//        FinalPriceBatingStrategyV1.default().let(::listOf).let { finalPriceBatingStrategyV1Repository.saveAll(it) }

        /*매수를 위한 microservice로 데이터 이관 로직*/
        /* event publish를 통해 해결*/
//        throw RuntimeException("일부로 throw")
    }

    @Recover
    suspend fun recover(e: Exception) {
        // 최대 재시도 횟수를 초과했을 때 수행할 동작
        println("재시도 횟수를 초과했습니다: ${e.message}")
        // 예: 알림 전송, 로그 기록, 대체 로직 실행 등
        TODO()
    }
}

//private fun makeMockProgramVolumeAdaptedList(): List<FinalPriceBatingStrategyV1> {
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
//}

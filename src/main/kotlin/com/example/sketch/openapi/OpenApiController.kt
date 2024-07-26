package com.example.sketch.openapi

import com.example.sketch.utils.OpenApiResponse
import com.example.sketch.utils.StringExtension.toRequestableDateFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/open-api")
@RestController
class OpenApiController(
    private val service: OpenApiService,
) {
    @PostMapping("/token")
    suspend fun login(): TokenResponse { // TODO: java와의 호환성을 위해 Mono타입으로 변경 필요, suspend 제거
        return service.requestToken()
    }

    @GetMapping("/current-price")
    suspend fun getCurrentPrice(): OpenApiResponse { // TODO: OpenApiResponse -> swagger response에 정확히 반영시킬 방법
        return service.getCurrentPrice()
    }

    @GetMapping("/current-price/investment") // 주식현재가 투자자[v1_국내주식-012]
    suspend fun getCurrentPriceOfInvestment(): OpenApiResponse {
        /**
         * 개요
         * 주식현재가 투자자 API입니다. 개인, 외국인, 기관 등 투자 정보를 확인할 수 있습니다.
         * [유의사항]
         * - 외국인은 외국인(외국인투자등록 고유번호가 있는 경우)+기타 외국인을 지칭합니다.
         * - 당일 데이터는 장 종료 후 제공됩니다.
         * */
        return service.getCurrentPriceOfInvestment()
    }

    @GetMapping("/program/individual/{stockId}") // 주식현재가 투자자[v1_국내주식-012]
    suspend fun getProgramTradeInfoPerIndividual(
        @PathVariable("stockId") stockId: String,
        @ModelAttribute request: GetProgramTradeInfoPerIndividualRequest,
    ): OpenApiResponse { // TODO: 날짜를 동적으로 조회 가능 하도록 변경
        /**
        개요
        국내주식 종목별 프로그램매매추이(일별) API입니다.
        한국투자 HTS(eFriend Plus) > [0465] 종목별 프로그램 매매추이 화면(혹은 한국투자 MTS > 국내 현재가 > 기타수급 > 프로그램) 의
        "일자별" 클릭 시 기능을 API로 개발한 사항으로,
        해당 화면을 참고하시면 기능을 이해하기 쉽습니다.
         * */
        return service.getProgramTradeInfoPerIndividual(stockId, request.toFormat())
    }

    @GetMapping("/program/individual/{stockId}/detail") // 주식현재가 투자자[v1_국내주식-012]
    suspend fun getProgramTradeInfoPerIndividualAtOneDay(
        @PathVariable("stockId") stockId: String,
    ): OpenApiResponse { // TODO: 시간을 동적으로 조회 가능 하도록 변경
        /**
        개요
        국내주식 종목별 프로그램매매추이(체결) API입니다.
        한국투자 HTS(eFriend Plus) > [0465] 종목별 프로그램 매매추이 화면(혹은 한국투자 MTS > 국내 현재가 > 기타수급 > 프로그램) 의 기능을 API로 개발한 사항으로,
        해당 화면을 참고하시면 기능을 이해하기 쉽습니다.
         * */
        return service.getProgramTradeInfoPerIndividualAtOneDay(stockId)
    }

    @GetMapping("/quotations/volume-rank") // 거래량순위[v1_국내주식-047]
    suspend fun getQuotationsOfVolumeRank(): OpenApiResponse {
        /**
        개요
        국내주식 거래량순위 API입니다.
        한국투자 HTS(eFriend Plus) > [0171] 거래량 순위 화면의 기능을 API로 개발한 사항으로, 해당 화면을 참고하시면 기능을 이해하기 쉽습니다.
        최대 30건 확인 가능하며, 다음 조회가 불가합니다.
        30건 이상의 목록 조회가 필요한 경우, 대안으로 종목조건검색 API를 이용해서 원하는 종목 100개까지 검색할 수 있는 기능을 제공하고 있습니다.
        종목조건검색 API는 HTS(efriend Plus) [0110] 조건검색에서 등록 및 서버저장한 나의 조건 목록을 확인할 수 있는 API로,
        HTS [0110]에서 여러가지 조건을 설정할 수 있는데, 그 중 거래량 순위(ex. 0봉전 거래량 상위순 100종목) 에 대해서도 설정해서 종목을 검색할 수 있습니다.
         **/
        return service.getQuotationsOfVolumeRank()
    }

    @GetMapping("/quotations/foreigner-trade-trend/{stockId}") // 거래량순위[v1_국내주식-047]
    suspend fun getForeignerTradeTrend(
        @PathVariable("stockId") stockId: String,
    ): OpenApiResponse {
        /**
        개요
        종목별 외국계 순매수추이 API입니다.
        한국투자 HTS(eFriend Plus) > [0433] 종목별 외국계 순매수추이 화면의 기능을 API로 개발한 사항으로, 해당 화면을 참고하시면 기능을 이해하기 쉽습니다.
         **/
        return service.getForeignerTradeTrend(stockId)
    }
}

data class GetProgramTradeInfoPerIndividualRequest(
    val date: String, // row date form 객체 만들어야 함.
) {
    fun toFormat(): String {
        return date.toRequestableDateFormat()
    }
}

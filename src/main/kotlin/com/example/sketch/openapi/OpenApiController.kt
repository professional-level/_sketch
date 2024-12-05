package com.example.sketch.openapi

import ApiResponse
import ProgramTradeVolume
import VolumeRank
import com.example.common.endpoint.Endpoint.GET_CURRENT_PRICE
import com.example.common.endpoint.Endpoint.GET_CURRENT_PRICE_OF_INVESTMENT
import com.example.common.endpoint.Endpoint.GET_EXECUTION_ORDERS
import com.example.common.endpoint.Endpoint.GET_FOREIGNER_TRADE_TREND
import com.example.common.endpoint.Endpoint.GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL
import com.example.common.endpoint.Endpoint.GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL_AT_ONE_DAY
import com.example.common.endpoint.Endpoint.GET_QUOTATIONS_OF_VOLUME_RANK
import com.example.common.endpoint.Endpoint.POST_STOCK_ORDER
import com.example.common.endpoint.Endpoint.REQUEST_TOKEN
import com.example.sketch.utils.OpenApiResponse
import com.example.sketch.utils.StringExtension.toRequestableDateFormat
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import output
import programStockList
import programStockOfDateTime
import programStockVolume
import stock
import stockMap
import stockOrder

@RequestMapping("/open-api")
@RestController
class OpenApiController(
    private val service: OpenApiService,
) {
    @PostMapping(REQUEST_TOKEN)
    suspend fun login(): TokenResponse { // TODO: java와의 호환성을 위해 Mono타입으로 변경 필요, suspend 제거
        return service.requestToken()
    }

    @GetMapping(GET_CURRENT_PRICE)
    suspend fun getCurrentPrice(): OpenApiResponse { // TODO: OpenApiResponse -> swagger response에 정확히 반영시킬 방법
        return service.getCurrentPrice()
    }

    @GetMapping(GET_CURRENT_PRICE_OF_INVESTMENT) // 주식현재가 투자자[v1_국내주식-012]
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

    @GetMapping(GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL) // 주식현재가 투자자[v1_국내주식-012]
    suspend fun getProgramTradeInfoPerIndividual(
        @PathVariable("stockId") stockId: String,
        @ModelAttribute request: GetProgramTradeInfoPerIndividualRequest,
        response: ServerHttpResponse,
    ): ProgramTradeVolume.ProgramStockList { // TODO: 날짜를 동적으로 조회 가능 하도록 변경
        /**
         개요
         국내주식 종목별 프로그램매매추이(일별) API입니다.
         한국투자 HTS(eFriend Plus) > [0465] 종목별 프로그램 매매추이 화면(혹은 한국투자 MTS > 국내 현재가 > 기타수급 > 프로그램) 의
         "일자별" 클릭 시 기능을 API로 개발한 사항으로,
         해당 화면을 참고하시면 기능을 이해하기 쉽습니다.
         * */
        return service.getProgramTradeInfoPerIndividual(stockId, request.toFormat())
            .toGetProgramTradeInfoPerIndividual()
    }

    @GetMapping(GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL_AT_ONE_DAY) // 일별 프로그램 거래대금 조회
    suspend fun getProgramTradeInfoPerIndividualAtOneDay(
        @PathVariable("stockId") stockId: String,
        response: ServerHttpResponse,
    ): ProgramTradeVolume.ProgramStockOfDateTime { // TODO: 시간을 동적으로 조회 가능 하도록 변경
        /**
         개요
         국내주식 종목별 프로그램매매추이(체결) API입니다.
         한국투자 HTS(eFriend Plus) > [0465] 종목별 프로그램 매매추이 화면(혹은 한국투자 MTS > 국내 현재가 > 기타수급 > 프로그램) 의 기능을 API로 개발한 사항으로,
         해당 화면을 참고하시면 기능을 이해하기 쉽습니다.
         * */
        return service.getProgramTradeInfoPerIndividualAtOneDay(stockId)
            .toGetProgramTradeInfoPerIndividualAtOneDayResponse()
//            .apply {
//                response.statusCode = when (rtCd == "0") {
//                    true -> HttpStatus.OK
//                    false -> HttpStatus.INTERNAL_SERVER_ERROR
//                }
//            }
    }

    @GetMapping(GET_QUOTATIONS_OF_VOLUME_RANK) // 거래량순위[v1_국내주식-047]
    suspend fun getQuotationsOfVolumeRank(): VolumeRank.StockMap {
        /**
         개요
         국내주식 거래량순위 API입니다.
         한국투자 HTS(eFriend Plus) > [0171] 거래량 순위 화면의 기능을 API로 개발한 사항으로, 해당 화면을 참고하시면 기능을 이해하기 쉽습니다.
         최대 30건 확인 가능하며, 다음 조회가 불가합니다.
         30건 이상의 목록 조회가 필요한 경우, 대안으로 종목조건검색 API를 이용해서 원하는 종목 100개까지 검색할 수 있는 기능을 제공하고 있습니다.
         종목조건검색 API는 HTS(efriend Plus) [0110] 조건검색에서 등록 및 서버저장한 나의 조건 목록을 확인할 수 있는 API로,
         HTS [0110]에서 여러가지 조건을 설정할 수 있는데, 그 중 거래량 순위(ex. 0봉전 거래량 상위순 100종목) 에 대해서도 설정해서 종목을 검색할 수 있습니다.
         **/
        return service.getQuotationsOfVolumeRank().toGetQuotationsOfVolumeRankResponse()
    }

    @GetMapping(GET_FOREIGNER_TRADE_TREND) //  종목별 외국계 순매수추이
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

    @PostMapping(POST_STOCK_ORDER)
    suspend fun postStockOrder(
        @RequestBody request: StockOrderRequest,
        response: ServerHttpResponse,
    ) {
        /**
         국내주식주문(현금) API 입니다.
         TTC0802U(현금매수) 사용하셔서 미수매수 가능합니다. 단, 거래하시는 계좌가 증거금40%계좌로 신청이 되어있어야 가능합니다.
         신용매수는 별도의 API가 준비되어 있습니다.
         ORD_QTY(주문수량), ORD_UNPR(주문단가) 등을 String으로 전달해야 함에 유의 부탁드립니다.
         ORD_UNPR(주문단가)가 없는 주문은 상한가로 주문금액을 선정하고 이후 체결이되면 체결금액로 정산됩니다.
         POST API의 경우 BODY값의 key값들을 대문자로 작성하셔야 합니다.
         (EX. "CANO" : "12345678", "ACNT_PRDT_CD": "01",...)
         종목코드 마스터파일 파이썬 정제코드는 한국투자증권 Github 참고 부탁드립니다.
         **/
        service.postStockOrder(request = request).toPostStockOrderResponse().apply {
            response.statusCode = when (rtCd == "0") {
                true -> HttpStatus.OK
                false -> HttpStatus.INTERNAL_SERVER_ERROR
            }
        }
    }

    @GetMapping(GET_EXECUTION_ORDERS)
    suspend fun getExecutionOrders() {
        service.getExecutionOrders()
    }
}

// TODO: 해당 to~로직을 다른 interface로 변경
private fun OpenApiResponse.toPostStockOrderResponse(): ApiResponse.StockOrder {
    val response = this.get(0)?.let {
        stockOrder {
            rtCd = it.get("rt_cd").asText()
            msgCd = it.get("msg_cd").asText()
            msg1 = it.get("msg_1").asText()
            output = output {
                kRXFWDGORDORGNO = it.get("KRX_FWDG_ORD_ORGNO").asText()
                oDNO = it.get("ODNO").asText()
                oRDTMD = it.get("ORD_TMD").asText()
            }
        }
    } ?: stockOrder {}
    return response
}

private fun OpenApiResponse.toGetProgramTradeInfoPerIndividualAtOneDayResponse(): ProgramTradeVolume.ProgramStockOfDateTime {
    val stock = this.get(0)?.let { // TODO: get()이 null일경우 처리
        programStockOfDateTime {
            bsopHour = it.get("bsop_hour").asText()
            stckPrpr = it.get("stck_prpr").asText()
            prdyVrss = it.get("prdy_vrss").asText()
            prdyVrssSign = it.get("prdy_vrss_sign").asText()
            prdyCtrt = it.get("prdy_ctrt").asText()
            acmlVol = it.get("acml_vol").asText()
            wholSmtnSelnVol = it.get("whol_smtn_seln_vol").asText()
            wholSmtnShnuVol = it.get("whol_smtn_shnu_vol").asText()
            wholSmtnNtbyQty = it.get("whol_smtn_ntby_qty").asText()
            wholSmtnSelnVol = it.get("whol_smtn_seln_tr_pbmn").asText()
            wholSmtnShnuVol = it.get("whol_smtn_shnu_tr_pbmn").asText()
            wholSmtnNtbyTrPbmn = it.get("whol_smtn_ntby_tr_pbmn").asText()
            wholNtbyVolIcdc = it.get("whol_ntby_vol_icdc").asText()
            wholNtbyTrPbmnIcdc = it.get("whol_ntby_tr_pbmn_icdc").asText()
//            rtCd = it.get("rt_cd").asText()
        }
    } ?: programStockOfDateTime {}
    return stock
}

private fun OpenApiResponse.toGetQuotationsOfVolumeRankResponse(): VolumeRank.StockMap {
    val stocks = this.map {
        it.get("mksc_shrn_iscd").asText() to
            stock {
                htsKorIsnm = it.get("hts_kor_isnm").asText()
                mkscShrnIscd = it.get("mksc_shrn_iscd").asText()
                dataRank = it.get("data_rank").asText()
                stckPrpr = it.get("stck_prpr").asText()
                prdyVrssSign = it.get("prdy_vrss_sign").asText()
                prdyVrss = it.get("prdy_vrss").asText()
                prdyCtrt = it.get("prdy_ctrt").asText()
                acmlVol = it.get("acml_vol").asText()
                prdyVol = it.get("prdy_vol").asText()
                lstnStcn = it.get("lstn_stcn").asText()
                avrgVol = it.get("avrg_vol").asText()
                nBefrClprVrssPrprRate = it.get("n_befr_clpr_vrss_prpr_rate").asText()
                volInrt = it.get("vol_inrt").asText()
                volTnrt = it.get("vol_tnrt").asText()
                ndayVolTnrt = it.get("nday_vol_tnrt").asText()
                avrgTrPbmn = it.get("avrg_tr_pbmn").asText()
                trPbmnTnrt = it.get("tr_pbmn_tnrt").asText()
                ndayTrPbmnTnrt = it.get("nday_tr_pbmn_tnrt").asText()
                acmlTrPbmn = it.get("acml_tr_pbmn").asText()
            }
    }
    return stockMap {
        stocks.forEach {
            items[it.first] = it.second
        }
    }
}

private fun OpenApiResponse.toGetProgramTradeInfoPerIndividual(): ProgramTradeVolume.ProgramStockList {
    val stocks = this.take(10).map {
        programStockVolume {
            stckBsopDate = it.get("stck_bsop_date").asText()
            stckClpr = it.get("stck_clpr").asText()
            prdyVrss = it.get("prdy_vrss").asText()
            prdyVrssSign = it.get("prdy_vrss_sign").asText()
            prdyCtrt = it.get("prdy_ctrt").asText()
            acmlVol = it.get("acml_vol").asText()
            acmlTrPbmn = it.get("acml_tr_pbmn").asText()
            wholSmtnSelnVol = it.get("whol_smtn_seln_vol").asText()
            wholSmtnShnuVol = it.get("whol_smtn_shnu_vol").asText()
            wholSmtnNtbyQty = it.get("whol_smtn_ntby_qty").asText()
            wholSmtnSelnVol = it.get("whol_smtn_seln_tr_pbmn").asText()
            wholSmtnShnuVol = it.get("whol_smtn_shnu_tr_pbmn").asText()
            wholSmtnNtbyTrPbmn = it.get("whol_smtn_ntby_tr_pbmn").asText()
            wholNtbyVolIcdc = it.get("whol_ntby_vol_icdc").asText()
            wholNtbyTrPbmnIcdc2 = it.get("whol_ntby_tr_pbmn_icdc2").asText()
        }
    }
    return programStockList {
        items.addAll(stocks)
    }
}

data class GetProgramTradeInfoPerIndividualRequest(
    val date: String, // row date form 객체 만들어야 함.
) {
    fun toFormat(): String {
        return date.toRequestableDateFormat()
    }
}
package com.example.sketch.configure

import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

class RequestTypeOverall(
    val type: RequestType,
) {
    val headers: Map<String, String> = mapOf()
    val parameters: RequestQueryParameter = RequestQueryParameter(mapOf())
}

enum class RequestType(
    // TODO: 전반적인 queryParameter를 반영하기 어려운 구조이므로 전체적인 리팩토링 필요
    val requestURI: String,
    val type: HttpMethod,
) {
    GET_TOKEN(requestURI = "/oauth2/tokenP", type = HttpMethod.POST),
    GET_CURRENT_PRICE(requestURI = "/uapi/domestic-stock/v1/quotations/inquire-price", type = HttpMethod.GET),
    GET_CURRENT_PRICE_OF_INVESTMENT(
        requestURI = "/uapi/domestic-stock/v1/quotations/inquire-investor",
        type = HttpMethod.GET,
    ),

    // TODO: Intellij Hard Wrap이 안먹히는 버그...수정
    GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL(
        requestURI = "/uapi/domestic-stock/v1/quotations/program-trade-by-stock-daily",
        type = HttpMethod.GET,
    ),
    GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL_AT_ONE_DAY(
        requestURI = "/uapi/domestic-stock/v1/quotations/program-trade-by-stock",
        type = HttpMethod.GET,
    ),
    GET_QUOTATIONS_OF_VOLUME_RANK(
        requestURI = "/uapi/domestic-stock/v1/quotations/volume-rank",
        type = HttpMethod.GET,
    ),
    GET_FOREIGNER_TRADE_TREND(
        requestURI = "/uapi/domestic-stock/v1/quotations/frgnmem-pchs-trend",
        type = HttpMethod.GET,
    ),
    POST_STOCK_ORDER(
        requestURI = "/uapi/domestic-stock/v1/trading/order-cash", // 주식주문(현금) URI
        type = HttpMethod.POST,
    ),
    ;

    fun getRequestUri() = requestURI

    fun getRequestType() = type
}

enum class HttpMethod {
    // TODO: 동일한 netty 레벨로 바꾸자
    GET,
    POST,
    PUT,
    DELETE,
    OPTION,
}

enum class QueryParameter(
    val default: String,
) {
    FID_COND_MRKT_DIV_CODE("J"), // 주식
    FID_INPUT_ISCD(emptyQueryParam), // 종목번호 6자리 ex) 삼성전자: 005930 or 입력 종목코드
    FID_INPUT_DATE_1(emptyQueryParam), // 기준일 기준일 (ex 0020240308) or 입력날짜 	""(공란) 입력 TODO: 날짜별 api로 만들 수 있는지 검증 필요
    FID_COND_SCR_DIV_CODE(emptyQueryParam), // 조건 화면 분류 코드
    FID_DIV_CLS_CODE(emptyQueryParam), // 분류 구분 코드 0(전체) 1(보통주) 2(우선주)
    FID_BLNG_CLS_CODE(emptyQueryParam), // 소속 구분 코드	0 : 평균거래량 1:거래증가율 2:평균거래회전율 3:거래금액순 4:평균거래금액회전율 TODO: 3이지만 동적 처리 필요
    FID_TRGT_CLS_CODE(emptyQueryParam), // 대상 구분 코드 1 or 0 9자리 (차례대로 증거금 30% 40% 50% 60% 100% 신용보증금 30% 40% 50% 60%)
    FID_TRGT_EXLS_CLS_CODE(emptyQueryParam), // 대상 제외 구분 코드 1 or 0 10자리 (차례대로 투자위험/경고/주의 관리종목 정리매매 불성실공시 우선주 거래정지 ETF ETN 신용주문불가 SPAC)
    FID_INPUT_ISCD_2("99999"), // 외국계 전체(99999)
    FID_INPUT_PRICE_1(emptyQueryParam), // 입력 가격1, 가격~, 전체검색 공란
    FID_INPUT_PRICE_2(emptyQueryParam), // 입력 가격 2 ~가격, 전체검색 공란
    FID_VOL_CNT(emptyQueryParam), // 거래량~
    ;

    companion object {
        private fun List<QueryParameter>.toResult(additionalInfo: Map<QueryParameter, String>): Map<QueryParameter, String> =
            associateWith { additionalInfo.getOrDefault(it, it.default) }

        fun forType(
            type: RequestType,
            additionalInfo: Map<QueryParameter, String>,
        ): Map<QueryParameter, String> =
            when (type) {
                RequestType.GET_TOKEN -> emptyList()
                RequestType.GET_CURRENT_PRICE -> {
                    requireNotNull(additionalInfo[FID_INPUT_ISCD])
                    listOf(FID_COND_MRKT_DIV_CODE, FID_INPUT_ISCD)
                }

                RequestType.GET_CURRENT_PRICE_OF_INVESTMENT -> {
                    requireNotNull(additionalInfo[FID_INPUT_ISCD])
                    listOf(FID_COND_MRKT_DIV_CODE, FID_INPUT_ISCD)
                }

                RequestType.GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL -> {
                    requireNotNull(additionalInfo[FID_INPUT_ISCD])
                    requireNotNull(additionalInfo[FID_INPUT_DATE_1])
                    listOf(
                        FID_COND_MRKT_DIV_CODE,
                        FID_INPUT_ISCD,
                        FID_INPUT_DATE_1,
                    )
                }

                RequestType.GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL_AT_ONE_DAY -> {
                    requireNotNull(additionalInfo[FID_INPUT_ISCD])
                    listOf(FID_INPUT_ISCD)
                }

                RequestType.GET_QUOTATIONS_OF_VOLUME_RANK ->
                    // TODO: validation 추가 필요
                    listOf(
                        FID_COND_MRKT_DIV_CODE,
                        FID_COND_SCR_DIV_CODE,
                        FID_INPUT_ISCD,
                        FID_DIV_CLS_CODE,
                        FID_BLNG_CLS_CODE,
                        FID_TRGT_CLS_CODE,
                        FID_TRGT_EXLS_CLS_CODE,
                        FID_INPUT_DATE_1,
                        FID_INPUT_PRICE_1,
                        FID_INPUT_PRICE_2,
                        FID_VOL_CNT,
                    )

                RequestType.GET_FOREIGNER_TRADE_TREND -> {
                    requireNotNull(additionalInfo[FID_INPUT_ISCD]) // TODO: requireNotNull에 해당하는지 아닌지를 객체 수준에서 식별이 가능해야 한다.
                    listOf(
                        FID_INPUT_ISCD,
                        FID_INPUT_ISCD_2,
                    )
                }

                RequestType.POST_STOCK_ORDER -> TODO()
            }.toResult(additionalInfo)
    }
}

data class RequestQueryParameter(
    val parameters: Map<QueryParameter, String>,
) {
    companion object {
        fun DEFAULT(): RequestQueryParameter = RequestQueryParameter(mapOf())
    }
}

fun WebClient.requestInfo(
    requestType: RequestType,
    queryParameter: Map<QueryParameter, String> = mapOf(),
): WebClient.RequestHeadersSpec<*> {
    val uriBuilder = UriComponentsBuilder.fromPath(requestType.getRequestUri())
    queryParameter.forEach { (key, value) ->
        uriBuilder.queryParam(key.name, value)
    }
    val uri = uriBuilder.toUriString()

    return when (requestType.getRequestType()) {
        HttpMethod.GET -> this.get().uri(uri)
        HttpMethod.POST -> this.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
        HttpMethod.PUT -> this.put().uri(uri)
        HttpMethod.DELETE -> this.delete().uri(uri)
        HttpMethod.OPTION -> this.options().uri(uri)
    }
}

// val emptyQueryParam: String get() = URLEncoder.encode("", StandardCharsets.UTF_8.toString()) // TODO: 최적화 가능한지 고려
const val emptyQueryParam = "\"\""
// const val emptyQueryParam = "0"
